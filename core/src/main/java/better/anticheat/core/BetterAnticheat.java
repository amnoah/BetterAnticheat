package better.anticheat.core;

import better.anticheat.core.check.CheckManager;
import better.anticheat.core.command.CommandManager;
import better.anticheat.core.configuration.ConfigSection;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public boolean entityTrackerFastAwaitingUpdate;
    
    // Auto-record settings
    private boolean autoRecordEnabled;
    private String autoRecordPermission;

    public BetterAnticheat(DataBridge dataBridge, Path directory, Lamp.Builder<?> lamp) {
        this.dataBridge = dataBridge;
        this.directory = directory;
        this.enabled = true;

        instance = this;

        this.checkManager = new CheckManager(this);
        this.commandManager = new CommandManager(this, lamp);
        this.lyricManager = new LyricManager();
        this.playerManager = new PlayerManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.recordingSaver = new RecordingSaver(directory);

        /*
         * We only support 1.21+.
         */
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21)) {
            dataBridge.logWarning("You are running on an unsupported version of Minecraft!");
            dataBridge.logWarning("Please update to 1.21 or above!");
            enabled = false;
        }
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

        ConfigSection settings = getFile("settings.yml", BetterAnticheat.class.getResourceAsStream("/settings.yml")).load();
        alertCooldown = settings.getObject(Integer.class, "alert-cooldown", 1000);
        verboseCooldownDivisor = settings.getObject(Double.class, "verbose-cooldown-divisor", 4.0);
        alertPermission = settings.getObject(String.class, "alert-permission", "better.anticheat");
        alertHover = settings.getList(String.class, "alert-hover");
        alertMessage = settings.getObject(String.class, "alert-message", "");
        clickCommand = settings.getObject(String.class, "click-command", "");
        punishmentModulo = settings.getObject(Boolean.class, "punishment-modulo", true);
        testMode = settings.getObject(Boolean.class, "test-mode", false);
        useCommand = settings.getObject(Boolean.class, "enable-commands", true);
        ignorePre121Players = settings.getObject(Boolean.class, "dont-inject-pre-121-players", true);

        final var webhookNode = settings.getConfigSection("webhook");
        if (webhookNode != null) {
            webhookUrl = webhookNode.getObject(String.class, "url", "");
            webhookMessage = webhookNode.getObject(String.class, "message", "**%username%** failed **%type%** (VL: %vl%)");
            saveWebhookUrl = webhookNode.getObject(String.class, "save-url", "");
        } else {
            webhookUrl = "";
            webhookMessage = "**%username%** failed **%type%** (VL: %vl%)";
            saveWebhookUrl = "";
        }


        final var combatMitigationNode = settings.getConfigSection("combat-damage-mitigation");

        if (combatMitigationNode == null) {
            // Default values if configuration section doesn't exist
            this.mitigationCombatDamageEnabled = true;
            this.mitigationCombatDamageCancellationChance = 20.0;
            this.mitigationCombatDamageTakenIncrease = 40.0;
            this.mitigationCombatDamageDealtDecrease = 40.0;
            this.mitigationCombatTickEnabled = true;
            this.mitigationCombatTickDuration = 3;
            this.mitigationCombatDamageHitregEnabled = false;
        } else {
            this.mitigationCombatDamageEnabled = combatMitigationNode
                    .getObject(Boolean.class, "enabled", false);
            this.mitigationCombatDamageCancellationChance = combatMitigationNode
                    .getObject(Double.class, "hit-cancellation-chance", 20.0);
            this.mitigationCombatDamageTakenIncrease = combatMitigationNode
                    .getObject(Double.class, "damage-taken-increase", 40.0);
            this.mitigationCombatDamageDealtDecrease = combatMitigationNode
                    .getObject(Double.class, "damage-reduction-multiplier", 40.0);
            this.mitigationCombatKnockbackDealtDecrease = combatMitigationNode
                    .getObject(Double.class, "velocity-dealt-reduction", 40.0);
            this.mitigationCombatDamageHitregEnabled = combatMitigationNode
                    .getObject(Boolean.class, "mess-with-hitreg", false);

            final var velocityTickCheckNode = combatMitigationNode.getConfigSection("tick-mitigation");
            if (velocityTickCheckNode != null) {
                this.mitigationCombatTickEnabled = velocityTickCheckNode.getObject(Boolean.class, "enabled", true);
                this.mitigationCombatTickDuration = velocityTickCheckNode.getObject(Integer.class, "min-ticks-since-last-attack", 4);
            } else {
                this.mitigationCombatTickEnabled = false;
                this.mitigationCombatTickDuration = 3;
            }
        }

        // Load auto-record settings
        final var autoRecordNode = settings.getConfigSection("auto-record");
        if (autoRecordNode != null) {
            this.autoRecordEnabled = autoRecordNode.getObject(Boolean.class, "enabled", false);
            this.autoRecordPermission = autoRecordNode.getObject(String.class, "permission", "better.anticheat.ml.record");
        } else {
            this.autoRecordEnabled = false;
            this.autoRecordPermission = "better.anticheat.ml.record";
        }

        this.entityTrackerFastAwaitingUpdate = settings.getObject(Boolean.class, "entity-tracker.fast-awaiting-update", false);

        loadML(settings);
        loadCookieAllocator(settings);

        punishmentManager.load();
        checkManager.load();
        this.lamp = commandManager.load();
        playerManager.load();

        dataBridge.logInfo("Load finished!");
    }

    public void loadML(final ConfigSection baseConfig) {
        final var mlNode = baseConfig.getConfigSection("ml");
        final var mlEnabled = mlNode.getObject(Boolean.class, "enabled", false);

        final var models = mlNode.getConfigSection("models");

        if (mlEnabled) {
            this.modelConfigs.clear();

            for (final var child : models.getChildren()) {
                this.modelConfigs.put(child.getKey(), new ModelConfig(
                        child.getObject(String.class, "display-name", "example-model"),
                        child.getObject(String.class, "type", "model-type"),
                        child.getObject(Integer.class, "slice", 1),
                        child.getList(String.class, "legit-dataset-names"),
                        child.getList(String.class, "cheat-dataset-names"),
                        child.getObject(Boolean.class, "statistics", false),
                        child.getObject(Boolean.class, "shrink", true),
                        child.getObject(Integer.class, "samples", 10),
                        child.getObject(Double.class, "alert-threshold", 7.5),
                        child.getObject(Double.class, "mitigation-threshold", 6.0),
                        child.getObject(Integer.class, "mitigation-only-ticks", 20),
                        child.getObject(Integer.class, "tree-depth", 35),
                        child.getObject(Integer.class, "node-size", 4),
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

        final var type = cookieNode.getObject(String.class, "type", "sequential");
        final var parametersNode = cookieNode.getConfigSection("parameters");

        final Map<String, Object> parameters = new HashMap<>();
        if (parametersNode != null) {
            // For file-based allocator
            if (parametersNode.hasNode("filename")) {
                parameters.put("filename", parametersNode.getObject(String.class, "filename", "cookie_sequences.txt"));
            }

            // For sequential allocator
            if (parametersNode.hasNode("startValue")) {
                parameters.put("startValue", parametersNode.getObject(Long.class, "startValue", 0L));
            }

            // For random allocator
            if (parametersNode.hasNode("cookieLength")) {
                parameters.put("cookieLength", parametersNode.getObject(Integer.class, "cookieLength", 8));
            }
            if (parametersNode.hasNode("maxRetries")) {
                parameters.put("maxRetries", parametersNode.getObject(Integer.class, "maxRetries", 100));
            }

            // For timestamp allocator
            if (parametersNode.hasNode("randomBytesLength")) {
                parameters.put("randomBytesLength", parametersNode.getObject(Integer.class, "randomBytesLength", 4));
            }

            // For lyric allocator
            if (parametersNode.hasNode("artist")) {
                parameters.put("artist", parametersNode.getObject(String.class, "artist", ""));
            }
            if (parametersNode.hasNode("title")) {
                parameters.put("title", parametersNode.getObject(String.class, "title", ""));
            }
            if (parametersNode.hasNode("maxLines")) {
                parameters.put("maxLines", parametersNode.getObject(Integer.class, "maxLines", 0));
            }
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

    public ConfigurationFile getFile(String name) {
        return new ConfigurationFile(name, directory);
    }

    public ConfigurationFile getFile(String name, InputStream input) {
        return new ConfigurationFile(name, directory, input);
    }

}
