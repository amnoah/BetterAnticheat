package better.anticheat.core.player.tracker.impl.confirmation;

import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.util.EasyLoops;
import better.anticheat.core.util.type.EvictingDeque;
import better.anticheat.core.util.type.incrementer.IntIncrementer;
import better.anticheat.core.util.type.incrementer.LongIncrementer;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCookieResponse;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConfirmationTracker extends Tracker {
    public static final String COOKIE_KEY = "ad-tracking-id";
    public static final String COOKIE_NAMESPACE = "bac";
    /**
     * A list of awaiting confirmations.
     */
    private final Set<ConfirmationState> confirmations = Collections.synchronizedSet(new HashSet<>());
    /**
     * 30 seconds of recent confirmations, assuming one confirmation per tick, but it is usually less than this, meaning it can provide an even longer window
     */
    private final EvictingDeque<ConfirmationState> recentConfirmations = new EvictingDeque<>(600);
    private final LongIncrementer cookieIncrementer = new LongIncrementer(Long.MIN_VALUE);
    private final LongIncrementer keepAliveIncrementer = new LongIncrementer(Short.MIN_VALUE);
    /**
     * If this is not null, the last sent confirmation, and when sent within 10ms of now, can be re-used.
     * This is used instead of a list search, to ensure we don't accidentally use a keepalive as the post confirmation, in a pre/post structure.
     */
    private @Nullable ConfirmationState lastSentConfirmation = null;

    public ConfirmationTracker(Player player) {
        super(player);
    }

    @Override
    public final void handlePacketPlayReceive(PacketPlayReceiveEvent event) {
        switch (event.getPacketType()) {
            case PONG:
                final var pongWrapper = new WrapperPlayClientPong(event);
                final var pongId = pongWrapper.getId();
                final var pongConfirmation = EasyLoops.findFirst(confirmations, state -> state.getId() == pongId && state.getType() == ConfirmationType.PINGPONG);
                if (pongConfirmation != null) {
                    pongConfirmation.setTimestampConfirmed(System.currentTimeMillis());
                    pongConfirmation.getListeners().forEach(Runnable::run);
                    confirmations.remove(pongConfirmation);
                    recentConfirmations.add(pongConfirmation);

                    if (pongConfirmation.isNeedsCancellation()) {
                        event.setCancelled(true);
                    }
                } else {
                    log.debug("[BetterAntiCheat] Received pong without confirmation: {}", pongId);
                }
                break;
            case KEEP_ALIVE:
                final var keepAliveWrapper = new WrapperPlayClientKeepAlive(event);
                final var keepAliveId = keepAliveWrapper.getId();
                final var keepAliveConfirmation = EasyLoops.findFirst(confirmations, state -> state.getId() == keepAliveId && state.getType() == ConfirmationType.KEEPALIVE);
                if (keepAliveConfirmation != null) {
                    keepAliveConfirmation.setTimestampConfirmed(System.currentTimeMillis());
                    keepAliveConfirmation.getListeners().forEach(Runnable::run);
                    confirmations.remove(keepAliveConfirmation);
                    recentConfirmations.add(keepAliveConfirmation);

                    if (keepAliveConfirmation.isNeedsCancellation()) {
                        event.setCancelled(true);
                    }
                } else {
                    log.debug("[BetterAntiCheat] Received keepalive without confirmation: {}", keepAliveId);
                }
                break;
            case COOKIE_RESPONSE:
                final var cookieWrapper = new WrapperPlayClientCookieResponse(event);
                final byte[] payload = cookieWrapper.getPayload();
                if (payload != null && payload.length >= 8) {
                    final long cookieId = ByteBuffer.wrap(payload).getLong();
                    final var cookieConfirmation = EasyLoops.findFirst(confirmations, state -> state.getId() == cookieId && state.getType() == ConfirmationType.COOKIE);
                    if (cookieConfirmation != null) {
                        cookieConfirmation.setTimestampConfirmed(System.currentTimeMillis());
                        cookieConfirmation.getListeners().forEach(Runnable::run);
                        confirmations.remove(cookieConfirmation);
                        recentConfirmations.add(cookieConfirmation);

                        if (cookieConfirmation.isNeedsCancellation()) {
                            event.setCancelled(true);
                        }
                    } else {
                        log.debug("[BetterAntiCheat] Received cookie response without confirmation: {}", cookieId);
                    }
                } else {
                    log.debug("[BetterAntiCheat] Received cookie response without payload");
                }
                break;
            case CLIENT_TICK_END:
                final long currentTime = System.currentTimeMillis();
                confirmations.removeIf(state -> {
                    if (state.getTimestampConfirmed() == -1L && currentTime - state.getTimestamp() > 60000) {
                        state.getListeners().forEach(Runnable::run);
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
                break;
            default:
                break;
        }
    }

    @Override
    public final void handlePacketPlaySend(PacketPlaySendEvent event) {
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

    /**
     * Sends a confirmation packet that can be subscribed to.
     */
    public CombinedConfirmation confirm() {
        final var now = System.currentTimeMillis();
        // Send last tick, and recent arrival.
        final var hasRecentArrival = !recentConfirmations.isEmpty() && now - recentConfirmations.getLast().getTimestampConfirmed() <= 50 && now - recentConfirmations.getLast().getTimestamp() <= 50;

        if (hasRecentArrival) {
            final var post = sendCookieOrLatest(now);
            final var acquiredConfirmation = new CombinedConfirmation(CompletableFuture.completedFuture(this.recentConfirmations.getLast()), new CompletableFuture<>(), new IntIncrementer(1));
            post.getListeners().add(() -> {
                acquiredConfirmation.getOnBegin().complete(this.recentConfirmations.isEmpty() ? null : this.recentConfirmations.getLast());
                acquiredConfirmation.getState().increment();
            });

            return acquiredConfirmation;
        }

        // Sent last tick, and NOT this tick (12ms ago for examplar), therefore is a perfect pre confirmation option
        var sentOption = EasyLoops.findFirst(confirmations, c -> c.getType() == ConfirmationType.KEEPALIVE & c.getTimestampConfirmed() == -1L & now - c.getTimestamp() <= 120 & now - c.getTimestamp() > 12);
        // Check if there is a sendoption except there is no keepalive sendoption.
        if (sentOption != null) {
            sentOption = EasyLoops.findFirst(confirmations, c -> c.getType() != ConfirmationType.KEEPALIVE & c.getTimestampConfirmed() == -1L & now - c.getTimestamp() <= 120 & now - c.getTimestamp() > 12);
        }

        if (sentOption != null) {
            final var post = sendCookieOrLatest(now);
            final var acquiredConfirmation = new CombinedConfirmation(new CompletableFuture<>(), new CompletableFuture<>(), new IntIncrementer(0));
            sentOption.getListeners().add(() -> {
                acquiredConfirmation.getOnBegin().complete(this.recentConfirmations.isEmpty() ? null : this.recentConfirmations.getLast());
                acquiredConfirmation.getState().increment();
            });

            post.getListeners().add(() -> {
                if (!acquiredConfirmation.getOnBegin().isDone()) {
                    acquiredConfirmation.getOnBegin().complete(post);
                    acquiredConfirmation.getState().increment();
                }

                acquiredConfirmation.getOnAfterConfirm().complete(post);
                acquiredConfirmation.getState().increment();
            });

            return acquiredConfirmation;
        }

        // Send a keepalive for pre I guess lol, and hope nothing breaks. If we are getting updates every tick, this will not be needed.
        final var acquiredConfirmation = new CombinedConfirmation(new CompletableFuture<>(), new CompletableFuture<>(), new IntIncrementer(0));
        final var id = this.keepAliveIncrementer.increment();
        final var confirmationState = new ConfirmationState(id, ConfirmationType.KEEPALIVE, now, true);
        this.confirmations.add(confirmationState);

        getPlayer().getUser().sendPacket(new WrapperPlayServerKeepAlive(
                id
        ));

        confirmationState.getListeners().add(() -> {
            acquiredConfirmation.getOnBegin().complete(confirmationState);
            acquiredConfirmation.getState().increment();
        });

        final var post = sendCookieOrLatest(now);
        post.getListeners().add(() -> {
            if (!acquiredConfirmation.getOnBegin().isDone()) {
                acquiredConfirmation.getOnBegin().complete(post);
                acquiredConfirmation.getState().increment();
            }

            acquiredConfirmation.getOnAfterConfirm().complete(post);
            acquiredConfirmation.getState().increment();
        });


        return acquiredConfirmation;
    }

    public ConfirmationState sendCookieOrLatest(final long now) {
        if (this.lastSentConfirmation == null || now - this.lastSentConfirmation.getTimestamp() > 10) {
            log.trace("[BetterAntiCheat] Sending cookie");
            final var id = this.cookieIncrementer.increment();
            getPlayer().getUser().writePacket(new WrapperPlayServerStoreCookie(
                    new ResourceLocation(COOKIE_NAMESPACE, COOKIE_KEY),
                    ByteBuffer.allocate(8).putLong(id).array()
            ));
            getPlayer().getUser().sendPacket(new WrapperPlayServerCookieRequest(
                    new ResourceLocation(COOKIE_NAMESPACE, COOKIE_KEY)
            ));
            this.lastSentConfirmation = new ConfirmationState(id, ConfirmationType.COOKIE, now, true);
            this.confirmations.add(this.lastSentConfirmation);
        }
        return this.lastSentConfirmation;
    }

    public void sendTickKeepaliveNoFlush() {
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

        getPlayer().getUser().writePacket(new WrapperPlayServerKeepAlive(
                id
        ));
    }
}
