package better.anticheat.core.player;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.DataBridge;
import better.anticheat.core.check.Check;
import better.anticheat.core.player.tracker.impl.ActionTracker;
import better.anticheat.core.player.tracker.impl.PlayerStatusTracker;
import better.anticheat.core.player.tracker.impl.PositionTracker;
import better.anticheat.core.player.tracker.impl.RotationTracker;
import better.anticheat.core.player.tracker.impl.confirmation.ConfirmationTracker;
import better.anticheat.core.player.tracker.impl.confirmation.cookie.CookieAllocatorConfig;
import better.anticheat.core.player.tracker.impl.confirmation.cookie.CookieIdAllocator;
import better.anticheat.core.player.tracker.impl.confirmation.cookie.allocator.*;
import better.anticheat.core.player.tracker.impl.entity.EntityTracker;
import better.anticheat.core.player.tracker.impl.mitigation.HitregMitigationTracker;
import better.anticheat.core.player.tracker.impl.ml.CMLTracker;
import better.anticheat.core.player.tracker.impl.teleport.TeleportTracker;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import lombok.Getter;
import lombok.Setter;
import wtf.spare.sparej.fastlist.FastObjectArrayList;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Player implements Closeable {

    private final BetterAnticheat plugin;
    private final User user;

    private final CMLTracker cmlTracker;
    private final ConfirmationTracker confirmationTracker;
    private final EntityTracker entityTracker;
    private final PositionTracker positionTracker;
    private final RotationTracker rotationTracker;
    private final PlayerStatusTracker playerStatusTracker;
    private final ActionTracker actionTracker;
    private final TeleportTracker teleportTracker;
    private final HitregMitigationTracker mitigationTracker;

    private List<Check> checks = null;

    @Setter
    private boolean alerts = false, verbose = false;

    private final List<Closeable> closeables = new ArrayList<>();
    private final List<Violation> violations = new FastObjectArrayList<>();

    public Player(final BetterAnticheat plugin, final User user, final DataBridge<?> dataBridge) {
        this.plugin = plugin;
        this.user = user;
        this.positionTracker = new PositionTracker(this);
        this.rotationTracker = new RotationTracker(this);

        // Create a separate cookie allocator instance for this player
        var cookieAllocator = createCookieAllocator(
                BetterAnticheat.getInstance().getCookieAllocatorConfig()
        );
        this.confirmationTracker = new ConfirmationTracker(this, cookieAllocator, dataBridge);

        this.entityTracker = new EntityTracker(this, this.confirmationTracker, this.positionTracker, dataBridge);
        this.playerStatusTracker = new PlayerStatusTracker(this, this.confirmationTracker);
        this.teleportTracker = new TeleportTracker(this);
        this.actionTracker = new ActionTracker(this);
        this.mitigationTracker = new HitregMitigationTracker(this, this.plugin);

        this.cmlTracker = new CMLTracker(this);
        load();

        closeables.add(dataBridge.registerTickListener(user, this.confirmationTracker::tick));

        // Attempt to check permissions now, may fail (due to threading or join state), that is okay, as we will re-check later.
        // This was meant to be here in the initial release, but I forgot lol.
        try {
            if (dataBridge.hasPermission(user, BetterAnticheat.getInstance().getAlertPermission())) {
                // Do not turn alerts off, in case during init it was already toggled, and if this fails in an undefined way due to threading on a weird platform, we could have issues.
                this.alerts = true;
            }
        } catch (Exception ignored) {
        }
    }

    /*
     * Handle packets.
     */
    public void handleReceivePacket(PacketPlayReceiveEvent event) {
        this.positionTracker.handlePacketPlayReceive(event);
        this.rotationTracker.handlePacketPlayReceive(event);
        this.confirmationTracker.handlePacketPlayReceive(event);
        this.entityTracker.handlePacketPlayReceive(event);
        this.playerStatusTracker.handlePacketPlayReceive(event);
        this.teleportTracker.handlePacketPlayReceive(event);
        this.cmlTracker.handlePacketPlayReceive(event);
        this.actionTracker.handlePacketPlayReceive(event);

        this.mitigationTracker.handlePacketPlayReceive(event);

        for (Check check : this.checks) {
            if (!check.getCheckConfig().isEnabled()) continue;
            check.handleReceivePlayPacket(event);
        }
    }

    public void handleSendPacket(PacketPlaySendEvent event) {
        this.positionTracker.handlePacketPlaySend(event);
        this.rotationTracker.handlePacketPlaySend(event);
        this.confirmationTracker.handlePacketPlaySend(event);
        this.entityTracker.handlePacketPlaySend(event);
        this.playerStatusTracker.handlePacketPlaySend(event);
        this.teleportTracker.handlePacketPlaySend(event);
        this.cmlTracker.handlePacketPlaySend(event);
        this.actionTracker.handlePacketPlaySend(event);

        for (Check check : this.checks) {
            if (!check.getCheckConfig().isEnabled()) continue;
            check.handleSendPlayPacket(event);
        }
    }

    /*
     *
     */

    public void load() {
        checks = plugin.getCheckManager().getChecksForPlayer(this);

        // Load CML generated checks.
        this.cmlTracker.onPlayerInit();
    }

    @Override
    public void close() throws IOException {
        if (this.cmlTracker.isRecordingNow()) {
            plugin.getRecordingSaver().savePlayerData(this, "quitsave-" + this.user.getName() + "-" + System.currentTimeMillis());
        }
        
        for (final var closeable : this.closeables) {
            if (closeable == null) continue;
            closeable.close();
        }
    }

    /**
     * Creates a new cookie ID allocator instance based on the provided configuration.
     * Each call creates a separate instance, ensuring per-player isolation.
     *
     * @param config the allocator configuration
     * @return a new CookieIdAllocator instance
     */
    private CookieIdAllocator createCookieAllocator(final CookieAllocatorConfig config) {
        if (config == null) {
            return new SequentialLongCookieAllocator();
        }

        try {
            switch (config.getType().toLowerCase()) {
                case "sequential":
                case "sequential_long":
                    return createSequentialAllocator(config);

                case "random":
                case "random_byte":
                    return createRandomAllocator(config);

                case "timestamp":
                case "timestamp_based":
                    return createTimestampAllocator(config);

                case "file":
                case "file_based":
                    return createFileAllocator(config);

                case "lyric":
                case "lyric_based":
                    return createLyricAllocator(config);

                default:
                    return new SequentialLongCookieAllocator();
            }
        } catch (final Exception e) {
            // Fall back to default if there's any error
            return new SequentialLongCookieAllocator();
        }
    }

    /**
     * Creates a sequential cookie allocator from the given configuration.
     *
     * @param config The configuration for the allocator.
     * @return A new sequential cookie allocator.
     */
    private CookieIdAllocator createSequentialAllocator(final CookieAllocatorConfig config) {
        final var params = config.getParameters();

        if (params.containsKey("startValue")) {
            final var startValueObj = params.get("startValue");
            long startValue;

            if (startValueObj instanceof Number) {
                startValue = ((Number) startValueObj).longValue();
            } else {
                startValue = Long.parseLong(startValueObj.toString());
            }

            return new SequentialLongCookieAllocator(startValue);
        }

        return new SequentialLongCookieAllocator();
    }

    /**
     * Creates a random byte cookie allocator from the given configuration.
     *
     * @param config The configuration for the allocator.
     * @return A new random byte cookie allocator.
     */
    private CookieIdAllocator createRandomAllocator(final CookieAllocatorConfig config) {
        final var params = config.getParameters();

        final var cookieLength = getIntParameter(params, "cookieLength", 8);
        final var maxRetries = getIntParameter(params, "maxRetries", 100);

        return new RandomByteCookieAllocator(cookieLength, maxRetries);
    }

    /**
     * Creates a timestamp-based cookie allocator from the given configuration.
     *
     * @param config The configuration for the allocator.
     * @return A new timestamp-based cookie allocator.
     */
    private CookieIdAllocator createTimestampAllocator(final CookieAllocatorConfig config) {
        final var params = config.getParameters();

        final var randomBytesLength = getIntParameter(params, "randomBytesLength", 8);

        return new TimestampBasedCookieAllocator(randomBytesLength);
    }

    /**
     * Creates a file-based cookie allocator from the given configuration.
     *
     * @param config The configuration for the allocator.
     * @return A new file-based cookie allocator.
     */
    private CookieIdAllocator createFileAllocator(final CookieAllocatorConfig config) {
        final var cookieSequenceData = BetterAnticheat.getInstance().getCookieSequenceData();

        if (cookieSequenceData == null) {
            // Fall back to sequential if no cookie sequence data is available
            return new SequentialLongCookieAllocator();
        }

        return new FileBasedCookieAllocator(cookieSequenceData);
    }

    /**
     * Creates a lyric-based cookie allocator from the given configuration.
     *
     * @param config The configuration for the allocator.
     * @return A new lyric-based cookie allocator.
     */
    private CookieIdAllocator createLyricAllocator(final CookieAllocatorConfig config) {
        final var params = config.getParameters();

        final var artist = (String) params.get("artist");
        final var title = (String) params.get("title");
        final var maxLines = getIntParameter(params, "maxLines", 0);

        if (artist == null || title == null) {
            throw new IllegalArgumentException("Artist and title must be specified for lyric cookie allocator.");
        }

        final var lyricSequenceData = BetterAnticheat.getInstance().getLyricManager().getLyricSequenceData(artist, title, maxLines);
        if (lyricSequenceData == null) {
            throw new IllegalStateException("Lyric sequence data not loaded for '" + artist + " - " + title + "'.");
        }

        return new LyricCookieAllocator(lyricSequenceData);
    }

    /**
     * Gets an integer parameter from a map, returning a default value if the key is not present or the value is not a valid integer.
     *
     * @param params       The map of parameters.
     * @param key          The key to look up.
     * @param defaultValue The default value to return if the key is not found or the value is invalid.
     * @return The parameter value or the default value.
     */
    private int getIntParameter(final java.util.Map<String, Object> params, final String key, final int defaultValue) {
        final var value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (final NumberFormatException e) {
                // Use default value if parsing fails
            }
        }
        return defaultValue;
    }
}