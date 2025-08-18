package better.anticheat.core;

import better.anticheat.core.check.CheckManager;
import better.anticheat.core.command.CommandManager;
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
    private static BetterAnticheat instance;

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
    private boolean punishmentModulo, testMode, useCommand, ignorePre121Players;
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

        configurationManager.registerDefaultConfig(
                "settings.conf",
                BetterAnticheat.class.getResourceAsStream("settings.conf")
        );
    }

    public void enable() {
        if (!enabled) return;
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this));
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

        alertCooldown = settings.getOrSetInteger("alert-cooldown", 1000);
        verboseCooldownDivisor = settings.getOrSetDouble("verbose-cooldown-divisor", 4);
        alertPermission = settings.getOrSetString("alert-permission", "better.anticheat");
        alertHover = settings.getOrSetStringList("alert-hover", Arrays.asList("&7Client Version: &c%clientversion%&7.", "&7Debug: &c%debug%&7.", "&7Click to teleport to the player!"));
        alertMessage = settings.getOrSetString("alert-message", "&c&lBA > &r&4%username% &7failed &4%type% &7VL: &4%vl%");
        clickCommand = settings.getOrSetString("click-command", "tp %username%");
        punishmentModulo = settings.getOrSetBoolean("punishment-modulo", true);

        testMode = settings.getOrSetBoolean("test-mode", false);
        useCommand = settings.getOrSetBoolean("enable-commands", true);
        ignorePre121Players = settings.getOrSetBoolean("dont-inject-pre-121-players", true);


        final var webhookNode = settings.getConfigSectionOrCreate("webhook");
        webhookUrl = webhookNode.getOrSetString("url", "");
        webhookMessage = webhookNode.getOrSetString("message", "**%username%** failed **%type%** (VL: %vl%)");
        saveWebhookUrl = webhookNode.getOrSetString("save-url", "");

        final var combatMitigationNode = settings.getConfigSectionOrCreate("combat-damage-mitigation");
        mitigationCombatDamageEnabled = combatMitigationNode.getOrSetBoolean("enabled", true);
        mitigationCombatDamageCancellationChance = combatMitigationNode.getOrSetDouble("hit-cancellation-chance", 20);
        mitigationCombatDamageTakenIncrease = combatMitigationNode.getOrSetDouble("damage-taken-increase", 40);
        mitigationCombatDamageDealtDecrease = combatMitigationNode.getOrSetDouble("damage-reduction-multiplier", 40);
        mitigationCombatKnockbackDealtDecrease = combatMitigationNode.getOrSetDouble("velocity-dealt-reduction", 40);
        mitigationCombatDamageHitregEnabled = combatMitigationNode.getOrSetBoolean("mess-with-hitreg", false);

        final var velocityTickNode = combatMitigationNode.getConfigSectionOrCreate("tick-mitigation");
        mitigationCombatTickEnabled = velocityTickNode.getOrSetBoolean("enabled", true);
        mitigationCombatTickDuration = velocityTickNode.getOrSetInteger("min-ticks-since-last-attack", 3);

        // Load auto-record settings
        final var autoRecordNode = settings.getConfigSectionOrCreate("auto-record");
        autoRecordEnabled = autoRecordNode.getOrSetBoolean("enabled", false);
        autoRecordPermission = autoRecordNode.getOrSetString("permission", "better.anticheat.ml.record");

        // This is true in the default config but we set it to false here so people updating their server without knowing about the new config do not get messed up.
        final var entityTracker = settings.getConfigSectionOrCreate("entity-tracker");
        entityTrackerFastAwaitingUpdate = entityTracker.getOrSetBoolean("fast-awaiting-update", false);
        entityTrackerFastEntityBox = entityTracker.getOrSetBoolean("fast-entity-box", false);

        loadML(settings);
        loadCookieAllocator(settings);

        punishmentManager.load();
        this.lamp = commandManager.load();
        playerManager.load();

        dataBridge.logInfo("Load finished!");
    }

    public void loadML(final ConfigSection baseConfig) {
        final var mlNode = baseConfig.getConfigSectionOrCreate("ml");
        final var mlEnabled = mlNode.getOrSetBoolean("enabled", false);

        final var models = mlNode.getConfigSectionOrCreate("models");

        if (mlEnabled) {
            this.modelConfigs.clear();

            for (final var child : models.getChildren()) {
                this.modelConfigs.put((String) child.getKey(), new ModelConfig(
                        child.getOrSetString("display-name", "example-model"),
                        child.getOrSetString("type", "model-type"),
                        child.getOrSetInteger("slice", 1),
                        child.getOrSetStringList("legit-dataset-names", Collections.emptyList()),
                        child.getOrSetStringList("cheat-dataset-names", Collections.emptyList()),
                        child.getOrSetBoolean("statistics", false),
                        child.getOrSetBoolean("shrink", true),
                        child.getOrSetInteger("samples", 10),
                        child.getOrSetDouble("alert-threshold", 7.5),
                        child.getOrSetDouble("mitigation-threshold", 6.0),
                        child.getOrSetInteger("mitigation-only-ticks", 20),
                        child.getOrSetInteger("tree-depth", 35),
                        child.getOrSetInteger("node-size", 4),
                        child
                ));
            }
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
        final var cookieNode = baseConfig.getConfigSection("cookie-allocator");

        if (cookieNode == null) {
            dataBridge.logInfo("No cookie allocator configuration found, using default sequential allocator");
            this.cookieAllocatorConfig = CookieAllocatorConfig.createDefault();
            return;
        }

        final var type = cookieNode.getOrSetString("type", "sequential");
        final var parametersNode = cookieNode.getConfigSection("parameters");

        final Map<String, Object> parameters = new HashMap<>();
        if (parametersNode != null) {
            // For file-based allocator
            parameters.put("filename", parametersNode.getOrSetString("filename", "cookie_sequences.txt"));

            // For sequential allocator
            parameters.put("startValue", parametersNode.getOrSetObject(Long.class, "startValue", 0L));

            // For random allocator
            parameters.put("cookieLength", parametersNode.getOrSetInteger("cookieLength", 8));
            parameters.put("maxRetries", parametersNode.getOrSetInteger("maxRetries", 100));

            // For timestamp allocator
            parameters.put("randomBytesLength", parametersNode.getOrSetInteger("randomBytesLength", 4));

            // For lyric allocator
            parameters.put("artist", parametersNode.getOrSetString("artist", ""));

            parameters.put("title", parametersNode.getOrSetString("title", ""));
            parameters.put("maxLines", parametersNode.getOrSetInteger("maxLines", 0));
        }

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
