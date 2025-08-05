package better.anticheat.core.player.tracker.impl.confirmation;

import better.anticheat.core.DataBridge;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.player.tracker.impl.confirmation.cookie.CookieIdAllocator;
import better.anticheat.core.util.EasyLoops;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCookieResponse;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import wtf.spare.sparej.EvictingDeque;
import wtf.spare.sparej.SimpleFuture;
import wtf.spare.sparej.fastlist.FastObjectArrayList;
import wtf.spare.sparej.incrementer.IntIncrementer;
import wtf.spare.sparej.incrementer.LongIncrementer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
public class ConfirmationTracker extends Tracker {
    public static final String COOKIE_KEY = "ad-tracking-id";
    public static final String COOKIE_NAMESPACE = "bac";
    /**
     * Application hooks
     */
    private final @NotNull DataBridge<?> dataBridge;
    /**
     * A list of awaiting confirmations.
     */
    private final Set<ConfirmationState> confirmations = Collections.synchronizedSet(
            // Default size is 16, so 32 is 2x the default, which is average of normal players and abusers.
            new ObjectLinkedOpenHashSet<>(32)
    );
    /**
     * 30 seconds of recent confirmations, assuming one confirmation per tick, but it is usually less than this, meaning it can provide an even longer window
     */
    private final EvictingDeque<ConfirmationState> recentConfirmations = new EvictingDeque<>(600);
    private final List<Runnable> removedItemTaskQueue = new FastObjectArrayList<>();
    private final CookieIdAllocator cookieIdAllocator;
    private final Object cookieLock = new Object();
    private final LongIncrementer keepAliveIncrementer = new LongIncrementer(Short.MIN_VALUE);
    /**
     * If this is not null, the last sent confirmation, and when sent within 10ms of now, can be re-used.
     * This is used instead of a list search, to ensure we don't accidentally use a keepalive as the post confirmation, in a pre/post structure.
     */
    private @Nullable ConfirmationState nextPostPacket = null;
    /**
     * Counts packet edges across flying types and client tick end to emulate end-of-tick cadence on older clients.
     */
    private int packetTickCounter = 0;

    public ConfirmationTracker(final @NotNull Player player, final @NotNull CookieIdAllocator cookieIdAllocator, final @NotNull DataBridge<?> dataBridge) {
        super(player);
        this.cookieIdAllocator = cookieIdAllocator;
        this.dataBridge = dataBridge;
    }

    @Override
    public final synchronized void handlePacketPlayReceive(final PacketPlayReceiveEvent event) {
        switch (event.getPacketType()) {
            case PONG: {
                final var pongWrapper = new WrapperPlayClientPong(event);
                final var pongId = pongWrapper.getId();
                final var pongConfirmation = EasyLoops.findFirst(confirmations, state ->
                        state.getType() == ConfirmationType.PINGPONG && state.hasLongId() && state.getLongId() == pongId);
                if (pongConfirmation != null) {
                    processConfirmationState(pongConfirmation);

                    if (pongConfirmation.isNeedsCancellation()) {
                        event.setCancelled(true);
                    }
                } else {
                    log.debug("[BetterAntiCheat] Received pong without confirmation: {}", pongId);
                }
                break;
            }
            case KEEP_ALIVE: {
                final var keepAliveWrapper = new WrapperPlayClientKeepAlive(event);
                final var keepAliveId = keepAliveWrapper.getId();
                final var keepAliveConfirmation = EasyLoops.findFirst(confirmations, state ->
                        state.getType() == ConfirmationType.KEEPALIVE && state.hasLongId() && state.getLongId() == keepAliveId);
                if (keepAliveConfirmation != null) {
                    processConfirmationState(keepAliveConfirmation);

                    if (keepAliveConfirmation.isNeedsCancellation()) {
                        event.setCancelled(true);
                    }
                } else {
                    log.debug("[BetterAntiCheat] Received keepalive without confirmation: {}", keepAliveId);
                }
                break;
            }
            case COOKIE_RESPONSE: {
                final var cookieWrapper = new WrapperPlayClientCookieResponse(event);
                final var payload = cookieWrapper.getPayload();
                if (payload != null && payload.length >= cookieIdAllocator.getCookieIdLength()) {
                    final var cookieConfirmation = EasyLoops.findFirst(confirmations, state ->
                            state.hasByteArrayId() &&
                                    state.getType() == ConfirmationType.COOKIE &&
                                    Arrays.equals(state.getByteArrayId(), payload)
                    );

                    if (cookieConfirmation != null) {
                        processConfirmationState(cookieConfirmation);

                        if (cookieConfirmation.isNeedsCancellation()) {
                            event.setCancelled(true);
                        }
                    } else {
                        log.debug("[BetterAntiCheat] Received cookie response without confirmation: {}", Arrays.toString(payload));
                    }
                } else {
                    log.debug("[BetterAntiCheat] Received cookie response with invalid payload length. Expected: {}, Got: {}",
                            cookieIdAllocator.getCookieIdLength(), payload != null ? payload.length : 0);
                }
                break;
            }
            case CLIENT_TICK_END: {
                // no-op; end-of-tick cadence is handled uniformly after the switch
                break;
            }
            default:
                break;
        }
        
        // Packet counter cadence: increment on flying or CLIENT_TICK_END and run the old CLIENT_TICK_END logic on even counts.
        final var packetType = event.getPacketType();
        if (WrapperPlayClientPlayerFlying.isFlying(packetType)
                || packetType == PacketType.Play.Client.CLIENT_TICK_END) {

            this.packetTickCounter++;
            if ((this.packetTickCounter % 2.0) == 0) {
                final var currentTime = System.currentTimeMillis();
                this.confirmations.removeIf(state -> {
                    if (state.getTimestampConfirmed() == -1L && currentTime - state.getTimestamp() > 60000) {
                        event.getPostTasks().add(() -> {
                            synchronized (this.removedItemTaskQueue) {
                                this.removedItemTaskQueue.addAll(state.getListeners());
                            }
                        });
                        if (state.getType() == ConfirmationType.COOKIE) {
                            getPlayer().getUser().sendPacket(new WrapperPlayServerDisconnect(Component.text("Timed out")));
                            log.info("[BetterAntiCheat] Timed out player: {}", getPlayer().getUser().getName());
                            getPlayer().getUser().closeConnection();
                        }
                        log.debug("[BetterAntiCheat] Timed out confirmation: {}", state);
                        return true;
                    }
                    return false;
                });

                event.getPostTasks().add(() -> {
                    synchronized (this.removedItemTaskQueue) {
                        this.removedItemTaskQueue.forEach(Runnable::run);
                        this.removedItemTaskQueue.clear();
                    }
                });
            }
        }
    }

    @Override
    public final synchronized void handlePacketPlaySend(final PacketPlaySendEvent event) {
        // Log shit that has been sent
        switch (event.getPacketType()) {
            case PING: {
                final var wrapper = new WrapperPlayServerPing(event);
                confirmations.add(new ConfirmationState(wrapper.getId(), ConfirmationType.PINGPONG, System.currentTimeMillis(), false));
                break;
            }
            case KEEP_ALIVE: {
                final var wrapper = new WrapperPlayServerKeepAlive(event);
                confirmations.add(new ConfirmationState(wrapper.getId(), ConfirmationType.KEEPALIVE, System.currentTimeMillis(), false));
                break;
            }
        }
    }

    private void processConfirmationState(final ConfirmationState state) {
        state.setTimestampConfirmed(System.currentTimeMillis());
        state.getListeners().forEach(Runnable::run);
        confirmations.remove(state);
        recentConfirmations.add(state);
    }

    /**
     * Sends a confirmation packet that can be subscribed to.
     *
     * @return A combined confirmation object.
     */
    public CombinedConfirmation confirm() {
        if (getPlayer().getUser().getConnectionState() != ConnectionState.PLAY) {
            final var last = this.recentConfirmations.peekLast();
            final var future = new SimpleFuture<ConfirmationState>();
            future.complete(last);
            return new CombinedConfirmation(future, future, new IntIncrementer(0));
        }
        final var now = System.currentTimeMillis();
        // Send last tick, and recent arrival.
        final var hasRecentArrival = !recentConfirmations.isEmpty() && now - recentConfirmations.getLast().getTimestampConfirmed() <= 50 && now - recentConfirmations.getLast().getTimestamp() <= 50;

        if (hasRecentArrival) {
            final var post = sendCookieOrLatest(now);
            final var acquiredConfirmation = new CombinedConfirmation(new SimpleFuture<>(this.recentConfirmations.getLast()), new SimpleFuture<>(), new IntIncrementer(1));
            post.getListeners().add(() -> {
                acquiredConfirmation.getOnAfterConfirm().complete(this.recentConfirmations.isEmpty() ? null : this.recentConfirmations.getLast());
                acquiredConfirmation.getState().increment();
            });

            return acquiredConfirmation;
        }

        // Is queued before this packet and not too old, therefore is a perfect pre confirmation option
        var sentOption = EasyLoops.findFirst(confirmations, c -> c.getType() == ConfirmationType.KEEPALIVE & c.getTimestampConfirmed() == -1L & now - c.getTimestamp() <= 120);
        // Check if there is a sendoption except there is no keepalive sendoption.
        if (sentOption != null) {
            sentOption = EasyLoops.findFirst(confirmations, c -> c.getType() != ConfirmationType.KEEPALIVE & c.getTimestampConfirmed() == -1L & now - c.getTimestamp() <= 120 & now - c.getTimestamp() > 12);
        }

        if (sentOption != null) {
            final var post = sendCookieOrLatest(now);
            final var acquiredConfirmation = new CombinedConfirmation(new SimpleFuture<>(), new SimpleFuture<>(), new IntIncrementer(0));
            sentOption.getListeners().add(() -> {
                if (acquiredConfirmation.getOnBegin().completeIfIncomplete(this.recentConfirmations.isEmpty() ? null : this.recentConfirmations.getLast())) {
                    acquiredConfirmation.getState().increment();
                }
            });

            post.getListeners().add(() -> {
                if (acquiredConfirmation.getOnBegin().completeIfIncomplete(post)) {
                    acquiredConfirmation.getState().increment();
                }

                acquiredConfirmation.getOnAfterConfirm().complete(post);
                acquiredConfirmation.getState().increment();
            });

            return acquiredConfirmation;
        }

        // Send a keepalive for pre I guess lol, and hope nothing breaks. If we are getting updates every tick, this will not be needed.
        final var acquiredConfirmation = new CombinedConfirmation(new SimpleFuture<>(), new SimpleFuture<>(), new IntIncrementer(0));
        final var id = this.keepAliveIncrementer.increment();
        final var confirmationState = new ConfirmationState(id, ConfirmationType.KEEPALIVE, now, true);
        this.confirmations.add(confirmationState);

        getPlayer().getUser().sendPacket(new WrapperPlayServerKeepAlive(
                id
        ));

        confirmationState.getListeners().add(() -> {
            if (acquiredConfirmation.getOnBegin().completeIfIncomplete(confirmationState)) {
                acquiredConfirmation.getState().increment();
                log.debug("[BetterAntiCheat] Completed begin confirmation normally...");
            }
        });

        final var post = sendCookieOrLatest(now);
        post.getListeners().add(() -> {
            if (!acquiredConfirmation.getOnBegin().completeIfIncomplete(post)) {
                acquiredConfirmation.getState().increment();

                log.debug("[BetterAntiCheat] Completed begin confirmation early...");
            }

            acquiredConfirmation.getOnAfterConfirm().complete(post);
            acquiredConfirmation.getState().increment();
        });


        return acquiredConfirmation;
    }

    /**
     * Sends a cookie or returns the last sent confirmation if it was sent recently.
     *
     * @param now The current time in milliseconds.
     * @return The confirmation state.
     */
    public ConfirmationState sendCookieOrLatest(final long now) {
        if (getPlayer().getUser().getConnectionState() != ConnectionState.PLAY) {
            return this.recentConfirmations.peekLast();
        }
        synchronized (cookieLock) {
            // Attempt to use native post confirmations to save on bandwidth.
            if (this.dataBridge.getNativeConfirmationHandler() instanceof PlatformNativeConfirmationHandler handler) {
                final var nativePostConfirmation = handler.confirmPost(getPlayer(), this::processConfirmationState);
                if (nativePostConfirmation != null) {
                    return nativePostConfirmation;
                }
            }

            if (this.nextPostPacket == null) {
                log.trace("[BetterAntiCheat] Constructing and Allocating cookie");
                final var cookieId = this.cookieIdAllocator.allocateNext();
                this.nextPostPacket = new ConfirmationState(cookieId, ConfirmationType.COOKIE, now + 1000, true);
            }
            return this.nextPostPacket;
        }
    }

    /**
     * Runs approximately once per server tick.
     * Sends a keepalive and flushes a cookie if needed.
     */
    public synchronized void tick() {
        if (getPlayer().getUser().getConnectionState() != ConnectionState.PLAY) {
            return;
        }

        // First, check if we can skip, because we already sent a keepalive and a cookie last tick
        final var now = System.currentTimeMillis();
        if (EasyLoops.anyMatch(this.confirmations, (c) -> c.getType() == ConfirmationType.KEEPALIVE & now - c.getTimestamp() < 55)
                && EasyLoops.anyMatch(this.confirmations, (c) -> c.getType() != ConfirmationType.KEEPALIVE & now - c.getTimestamp() < 55)) {
            log.trace("[BetterAntiCheat] Skipping tick keepalive");
            return;
        }
        // Otherwise, send it
        log.trace("[BetterAntiCheat] Sending tick keepalive");
        final var id = this.keepAliveIncrementer.increment();

        final var confirmationState = new ConfirmationState(id, ConfirmationType.KEEPALIVE, now, true);
        this.confirmations.add(confirmationState);

        try {
            getPlayer().getUser().writePacket(new WrapperPlayServerKeepAlive(
                    id
            ));

            synchronized (cookieLock) {
                final var post = this.nextPostPacket;
                if (post != null) {
                    getPlayer().getUser().writePacket(new WrapperPlayServerStoreCookie(
                            new ResourceLocation(COOKIE_NAMESPACE, COOKIE_KEY),
                            post.getByteArrayId()
                    ));
                    getPlayer().getUser().sendPacket(new WrapperPlayServerCookieRequest(
                            new ResourceLocation(COOKIE_NAMESPACE, COOKIE_KEY)
                    ));
                    log.trace("[BetterAntiCheat] Flushing cookie");
                    this.confirmations.add(post);
                }

                this.nextPostPacket = null;
            }
        } catch (final NullPointerException ignoredFailedClose) {
            try {
                // The player is a ghost.
                this.getPlayer().close();
                this.getPlayer().getUser().closeConnection();
            } catch (final Exception e) {
                log.error("[BetterAntiCheat] Failed to and log off player", e);
            }
        }
    }
}