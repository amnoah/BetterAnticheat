package better.anticheat.core;

import better.anticheat.core.check.CheckManager;
import better.anticheat.core.command.CommandManager;
import better.anticheat.core.command.CommandPacketListener;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.configuration.ConfigurationManager;
import better.anticheat.core.punishment.PunishmentManager;
import better.anticheat.core.configuration.ConfigurationFile;
import better.anticheat.core.player.PlayerManager;
import better.anticheat.core.player.tracker.impl.confirmation.cookie.CookieAllocatorConfig;
import better.anticheat.core.player.tracker.impl.confirmation.cookie.CookieSequenceData;
import better.anticheat.core.player.tracker.impl.confirmation.cookie.LyricManager;
import better.anticheat.core.util.ml.MLTrainer;
import better.anticheat.core.util.ml.ModelConfig;
import better.anticheat.core.util.ml.RecordingSaver;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import lombok.Getter;
import revxrsal.commands.Lamp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

@Getter
public class BetterAnticheat {

    @Getter
    @Deprecated
    /*
     * Going forward, we are going to avoid using static like this. You should not have a static manager if it needs
     * something like this, which would usually be passed in a constructor. Instead, make it a proper object!
     * Static access of the BetterAnticheat class will be removed soon. If you are currently using it, don't.
     */
    private static BetterAnticheat instance;

    private final static List<ModelConfig> DEFAULT_ML_CONFIGS = new ArrayList<>();

    static {
        DEFAULT_ML_CONFIGS.add(new ModelConfig(
                "raw-data-included-v0",
                "Raw Data",
                "decision_tree_gini",
                0,
                List.of("legit-small-2025-06-24-1", "legit-small-2025-07-29-1"),
                List.of("cheat-small-2025-06-24-1", "cheat-small-2025-07-29-1"),
                false,
                false,
                15,
                7.5,
                6,
                10,
                35,
                4,
                null
        ));
        DEFAULT_ML_CONFIGS.add(new ModelConfig(
                "statistics-included-v0",
                "Settings",
                "decision_tree_entropy",
                2,
                List.of("legit-small-2025-06-24-1", "legit-small-2025-07-29-1"),
                List.of("cheat-small-2025-06-24-1", "cheat-small-2025-07-29-1"),
                true,
                false,
                20,
                6,
                5,
                10,
                30,
                4,
                null
        ));
    }

    // Constructor-related objs
    private final DataBridge dataBridge;
    private final Path directory;
    private Lamp lamp;
    private boolean enabled;

    // Managers
    private final CheckManager checkManager;
    private final CommandManager commandManager;
    private final ConfigurationManager configurationManager;
    private final LyricManager lyricManager;
    private final PlayerManager playerManager;
    private final PunishmentManager punishmentManager;
    private final RecordingSaver recordingSaver;

    // Settings
    private int alertCooldown;
    private double verboseCooldownDivisor;
    private List<String> alertHover;
    private String alertMessage, alertPermission, clickCommand;
    private boolean punishmentModulo, testMode, ignorePre121Players;
    private String webhookUrl, webhookMessage, saveWebhookUrl;
    private final Map<String, ModelConfig> modelConfigs = new Object2ObjectArrayMap<>();
    private boolean mitigationCombatDamageEnabled;
    private double mitigationCombatDamageCancellationChance;
    private double mitigationCombatDamageTakenIncrease;
    private double mitigationCombatDamageDealtDecrease;
    private double mitigationCombatKnockbackDealtDecrease;
    private boolean mitigationCombatTickEnabled;
    private int mitigationCombatTickDuration;
    private boolean mitigationCombatDamageHitregEnabled;
    private CookieAllocatorConfig cookieAllocatorConfig;
    private CookieSequenceData cookieSequenceData;
    private boolean entityTrackerFastAwaitingUpdate;
    private boolean entityTrackerFastEntityBox;
    
    // Auto-record settings
    private boolean autoRecordEnabled;
    private String autoRecordPermission;

    public BetterAnticheat(DataBridge dataBridge, Path directory, Lamp.Builder<?> lamp) {
        this.dataBridge = dataBridge;
        this.directory = directory;
        this.enabled = true;

        instance = this;

        this.configurationManager = new ConfigurationManager(this);

        this.checkManager = new CheckManager(this);
        this.recordingSaver = new RecordingSaver(directory);
        this.commandManager = new CommandManager(this, lamp);
        this.lyricManager = new LyricManager();
        this.playerManager = new PlayerManager(this);
        this.punishmentManager = new PunishmentManager(this);

        /*
         * We only support 1.21+.
         */
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21)) {
            dataBridge.logWarning("You are running on an unsupported version of Minecraft!");
            dataBridge.logWarning("Please update to 1.21 or above!");
            enabled = false;
            return;
        }
    }

    public void enable() {
        if (!enabled) return;
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this));
        PacketEvents.getAPI().getEventManager().registerListeners(new CommandPacketListener(this));
        load();

        // Ensure players are 1.21+. We will conditionally load checks depending on features (e.g., CLIENT_TICK_END).
        playerManager.registerQuantifier((user -> !ignorePre121Players || user.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21)));
    }

    public void disable() {
        enabled = false;
    }

    public void load() {
        if (!enabled) return;

        dataBridge.logInfo("Beginning load!");

        configurationManager.load();

        ConfigSection settings = configurationManager.getConfigurationFile("settings.conf").getRoot();
        if (settings == null) return;

        // Load general settings.conf settings.

        alertCooldown = settings.getOrSetIntegerWithComment(
                "alert-cooldown",
                1000,
                "How long in ms should it be before a given check can send another alert."
        );
        verboseCooldownDivisor = settings.getOrSetDoubleWithComment(
                "verbose-cooldown-divisor",
                4,
                """
                        How much should we divide the alert cooldown by for verbose alerts?
                        If you keep alert cooldown at 1000ms, then verbose cooldown is the max amount of alerts per player per check per second."""
        );
        alertPermission = settings.getOrSetStringWithComment(
                "alert-permission",
                "better.anticheat",
                "Users with this permission will receive alert messages."
        );
        alertHover = settings.getOrSetStringListWithComment(
                "alert-hover",
                Arrays.asList("&7Client Version: &c%clientversion%&7.", "&7Debug: &c%debug%&7.", "&7Click to teleport to the player!"),
                """
                        What should appear when an alert is hovered over?
                        Set to [] to disable.
                        Available Placeholders:
                        %clientversion% - The player's Minecraft version.
                        %debug% - Any debug the check outputs."""
        );
        alertMessage = settings.getOrSetStringWithComment(
                "alert-message",
                "&c&lBA > &r&4%username% &7failed &4%type% &7VL: &4%vl%",
                """
                        What message should be displayed when a check is failed?
                        Set to "" to disable.
                        Available Placeholders:
                        %type% - The check that was failed.
                        %vl% - The amount of times this player has failed the check.
                        %username% - The username of the player who failed the check."""
        );
        clickCommand = settings.getOrSetStringWithComment(
                "click-command",
                "tp %username%",
                """
                        What command should be run when an alert message is clicked on?
                        Set to "" to disable.
                        Available Placeholders:
                        %username% - The username of the player who failed the check."""
        );
        punishmentModulo = settings.getOrSetBooleanWithComment(
                "punishment-modulo",
                true,
                """
                        If true, punishments will be delivered if current VL is divisible by the punishment amount.
                        Ex: At 8 vls, punishments set for 8, 4, 2, and 1 would run. Punishments set for 3, 5, 6, and 7 wouldn't.
                        If false, punishments will be delivered at the written vl.
                        Ex: At 8 vls, punishments set for 8 would run. Punishments set for 1, 2, 3, 4, 5, 6, and 7 wouldn't."""
        );
        ignorePre121Players = settings.getOrSetBooleanWithComment(
                "dont-inject-pre-121-players",
                true,
                "Ignore pre-1.21 players to avoid compatibility issues, within the anticheat."
        );
        testMode = settings.getOrSetBooleanWithComment(
                "test-mode",
                false,
                "Sends alerts only to the user who triggered it. Used for testing purposes."
        );

        // Webhook management.

        final var webhookNode = settings.getConfigSectionOrCreate("webhook");
        webhookUrl = webhookNode.getOrSetStringWithComment(
                "url",
                "",
                "The URL of the webhook to send messages to. Set to \"\" to disable."
        );
        webhookMessage = webhookNode.getOrSetStringWithComment(
                "message",
                "**%username%** failed **%type%** (VL: %vl%)",
                "The message to send to the webhook."
        );
        saveWebhookUrl = webhookNode.getOrSetStringWithComment(
                "save-url",
                "",
                """
                        The URL of the webhook to send recording saves to (optional).
                        If set to "", it will use the main webhook URL instead."""
        );

        // Handle combat mitigation.

        final var combatMitigationNode = settings.getConfigSectionOrCreate("combat-damage-mitigation");
        mitigationCombatDamageEnabled = combatMitigationNode.getOrSetBooleanWithComment(
                "enabled",
                true,
                """
                        Whether to enable ML-based combat damage modification
                        Only works when ML is enabled."""
        );
        mitigationCombatDamageCancellationChance = combatMitigationNode.getOrSetDoubleWithComment(
                "hit-cancellation-chance",
                20,
                "Multiplier for hit cancellation chance (average * multiplier = % chance), average is 1-10, where 10 is definitely cheating, and 1 is not cheating."
        );
        mitigationCombatDamageTakenIncrease = combatMitigationNode.getOrSetDoubleWithComment(
                "damage-taken-increase",
                40,
                """
                        Multiplier for damage increase calculation. Will increase damage taken by increase%.
                        Not supported on Sponge and Velocity."""
        );
        mitigationCombatDamageDealtDecrease = combatMitigationNode.getOrSetDoubleWithComment(
                "damage-dealt-reduction",
                40,
                """
                        Multiplier for damage reduction calculation. Will reduce damage dealt by reduction%.
                        Not supported on Sponge and Velocity."""
        );
        mitigationCombatKnockbackDealtDecrease = combatMitigationNode.getOrSetDoubleWithComment(
                "velocity-dealt-reduction",
                40,
                """
                        Multiplier for knockback reduction calculation. Will reduce velocity by reduction%.
                        Not supported on Sponge and Velocity."""
        );
        mitigationCombatDamageHitregEnabled = combatMitigationNode.getOrSetBooleanWithComment(
                "mess-with-hitreg",
                false,
                """
                        Mess with hitreg to make life horrible for cheaters? Works by giving the person who is attacking the cheater server-side hitbox and reach cheats.
                        Can break other anticheat's reach and hitbox checks.
                        Is probably the most OP mitigation."""
        );

        final var combatTickNode = combatMitigationNode.getConfigSectionOrCreate("tick-mitigation");
        mitigationCombatTickEnabled = combatTickNode.getOrSetBooleanWithComment(
                "enabled",
                true,
                "Tick-based attack cancellation. Is effectively a cps limit for cheaters."
        );
        mitigationCombatTickDuration = combatTickNode.getOrSetIntegerWithComment(
                "min-ticks-since-last-attack",
                3,
                "Minimum number of ticks since the last attack, in order to allow a new attack."
        );

        // Load auto-record settings
        final var autoRecordNode = settings.getConfigSectionOrCreate("auto-record");
        autoRecordEnabled = autoRecordNode.getOrSetBooleanWithComment(
                "enabled",
                false,
                "Whether to automatically enable machine learning recording on the first hit if the user has the required permission."
        );
        autoRecordPermission = autoRecordNode.getOrSetStringWithComment(
                "permission",
                "better.anticheat.ml.record",
                """
                        The permission required to automatically enable recording on first hit
                        Only give this to players who are guaranteed not to cheat (like staff)."""
        );

        // This is true in the default config but we set it to false here so people updating their server without knowing about the new config do not get messed up.
        final var entityTracker = settings.getConfigSectionOrCreate("entity-tracker");
        entityTrackerFastAwaitingUpdate = entityTracker.getOrSetBooleanWithComment(
                "fast-awaiting-update",
                true,
                """
                        Changes the way the entity tracker handles ticking interpolation when new movements are in flight.
                        This improves performance but may reduce strictness slightly."""
        );
        entityTrackerFastEntityBox = entityTracker.getOrSetBooleanWithComment(
                "fast-entity-box",
                false,
                """
                        Uses a new single box containing all other entity boxes instead of iterating over existing boxes when ray tracing.
                        Is usually faster but may reduce strictness slightly."""
        );

        loadML(settings);
        loadCookieAllocator(settings);

        punishmentManager.load();
        this.lamp = commandManager.load();
        playerManager.load();

        configurationManager.save();

        dataBridge.logInfo("Load finished!");
    }

    public void loadML(final ConfigSection baseConfig) {
        modelConfigs.clear();
        final var mlNode = baseConfig.getConfigSectionOrCreateWithComment(
                """
                        NOTE: These features are currently highly experimental, and are released for development purposes only.
                        DO NOT USE THEM IN PRODUCTION ENVIRONMENTS WITHOUT THOROUGH TESTING
                        MAKING THIS FEATURE STABLE will likely require significant and diverse amounts of extra training data, which can be collected with the record commands.""",
                "ml"
        );
        final var mlEnabled = mlNode.getOrSetBooleanWithComment("enabled", false, "Whether to enable ML combat features.");
        if (!mlEnabled) return;

        boolean hasModels = mlNode.hasNode("models");
        final var models = mlNode.getConfigSectionOrCreateWithComment(
                "The list of models to use. Note that this does not update when the plugin is updated, so check the wiki for the latest recommended configuration, after upgrades.",
                "models"
        );


        if (!hasModels) {
            for (final var defaultConfig : DEFAULT_ML_CONFIGS) {
                models.setObject(ModelConfig.class, defaultConfig.getNode(), defaultConfig);
            }
        }

        for (final var child : models.getChildren()) {
            Optional<ModelConfig> conf = models.getObject(ModelConfig.class, child.getKey());
            if (conf.isEmpty()) continue;
            modelConfigs.put((String) child.getKey(), conf.get());
        }

        this.modelConfigs.forEach((name, config) -> {
            try {
                this.dataBridge.logInfo("Loading model for " + name + "...");
                final var model = MLTrainer.create(config.getLegitDatasetNames(), config.getCheatDatasetNames(), config.getType(), config.getSlice(), config.isStatistics(), config.isStatistics(), config.isShrink(), config.getTreeDepth(), config.getNodeSize(), this.directory);
                config.setClassifierFunction(model);
                this.dataBridge.logInfo("Model for " + name + " loaded!");
            } catch (IOException e) {
                this.dataBridge.logWarning("Error while creating model trainer for " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Loads the cookie allocator configuration from the provided base configuration.
     *
     * @param baseConfig The base configuration section.
     */
    public void loadCookieAllocator(final ConfigSection baseConfig) {
        final var cookieNode = baseConfig.getConfigSectionOrCreate("cookie-allocator");

        final var type = cookieNode.getOrSetStringWithComment(
                "type",
                "sequential",
                """
                        The type of cookie ID allocator to use.
                        Options: "sequential", "random", "timestamp", "file", "lyric\""""
        );
        final var parametersNode = cookieNode.getConfigSectionOrCreate("parameters");

        final Map<String, Object> parameters = new HashMap<>();

        // For file-based allocator
        parameters.put("filename", parametersNode.getOrSetStringWithComment(
                "filename",
                "cookie_sequences.txt",
                """
                        For "file" allocator:
                        The name of the file containing cookie sequences. Default: alphabet.txt
                        Files can be placed in src/main/resources/ (for inclusion in the plugin JAR)
                        or in {BetterAnticheat.directory}/cookiesequence/ (for external loading)."""
                )
        );

        // For sequential allocator
        parameters.put("startValue", parametersNode.getOrSetObjectWithComment(
                Long.class,
                "startValue",
                0L,
                """
                        For "sequential" allocator:
                        The starting value for the sequential cookie IDs. Default: 0"""
                )
        );

            // For random allocator
            parameters.put("cookieLength", parametersNode.getOrSetIntegerWithComment(
                    "cookieLength",
                    8,
                    """
                            For "random" allocator:
                            The length of generated cookie IDs in bytes. Default: 8"""
                    )
            );
            parameters.put("maxRetries", parametersNode.getOrSetIntegerWithComment(
                    "maxRetries",
                    100,
                    "Maximum retries for ensuring uniqueness of random cookie IDs. Default: 100"
                    )
            );

            // For timestamp allocator
            parameters.put("randomBytesLength", parametersNode.getOrSetIntegerWithComment(
                    "randomBytesLength",
                    4,
                    """
                            For "timestamp" allocator:
                            The number of random bytes to append to the timestamp. Default: 4"""
                    )
            );

            // For lyric allocator
            parameters.put("artist", parametersNode.getOrSetStringWithComment(
                    "artist",
                    "",
                    """
                            For "lyric" allocator, here are some songs that work well:
                            
                            Recommended #1: Artist: "Lana Del Rey", Song: "God Bless America - And All The Beautiful Women In It"
                            Recommended #2: Artist: "2 Live Crew", Song: "The Fuck Shop"
                            Recommended #3: Artist: "Metallica" - Song: "So What"
                            Recommended #4: Artist: "Mao Ze" - Song: "Red Sun in the Sky"
                            Recommended #5: Artist: "Rihanna" - Song: "Diamonds"
                            
                            The artist of the song for lyric cookies. Default: "\""""
                    )
            );
            parameters.put("title", parametersNode.getOrSetStringWithComment(
                    "title",
                    "",
                    """
                            The title of the song for lyric cookies. Default: ""
                            The song must have at least 50 lines of lyrics."""
                    )
            );
            parameters.put("maxLines", parametersNode.getOrSetIntegerWithComment(
                    "maxLines",
                    0,
                    "The maximum number of lyric lines to use (0 for all). Default: 0"
                    )
            );

        this.cookieAllocatorConfig = new CookieAllocatorConfig(type, parameters);

        // If this is a file-based allocator, load the cookie sequence data during initialization
        if ("file".equalsIgnoreCase(type) || "file_based".equalsIgnoreCase(type)) {
            final var filename = (String) parameters.getOrDefault("filename", "cookie_sequences.txt");
            try {
                this.cookieSequenceData = new CookieSequenceData(filename);
                dataBridge.logInfo("Loaded cookie sequence data for file-based allocator: " + filename);
            } catch (final Exception e) {
                dataBridge.logWarning("Failed to load cookie sequence data for file '" + filename + "': " + e.getMessage());
                dataBridge.logWarning("Falling back to default sequential allocator");
                this.cookieAllocatorConfig = CookieAllocatorConfig.createTimestamp(10);
                this.cookieSequenceData = null;
            }
        } else if ("lyric".equalsIgnoreCase(type) || "lyric_based".equalsIgnoreCase(type)) {
            final var artist = (String) parameters.get("artist");
            final var title = (String) parameters.get("title");
            final var maxLines = (Integer) parameters.getOrDefault("maxLines", 0);

            if (artist == null || title == null) {
                dataBridge.logWarning("Artist and title must be specified for lyric cookie allocator. Falling back to default sequential allocator.");
                this.cookieAllocatorConfig = CookieAllocatorConfig.createTimestamp(10);
                return;
            }

            final var lyrics = this.lyricManager.getLyricSequenceData(artist, title, maxLines);
            if (lyrics == null) {
                dataBridge.logWarning("Lyric sequence data not loaded for '" + artist + " - " + title + "'. Falling back to default sequential allocator.");
                this.cookieAllocatorConfig = CookieAllocatorConfig.createTimestamp(10);
                return;
            }

            if (lyrics.getAvailableLyricCount() < 50) {
                dataBridge.logWarning("Not enough lyric sequences available for '" + artist + " - " + title + "'. Consider choosing a different song, or setting maxLines to 0.");
                this.cookieAllocatorConfig = CookieAllocatorConfig.createSequential(Integer.MIN_VALUE / 2);
                return;
            }
        }

        dataBridge.logInfo("Loaded cookie allocator configuration: type=" + type + ", parameters=" + parameters);
    }
}