package better.anticheat.core;

import better.anticheat.core.check.CheckManager;
import better.anticheat.core.command.BetterAnticheatCommand;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.configuration.ConfigurationFile;
import better.anticheat.core.player.PlayerManager;
import better.anticheat.core.util.ml.MLTrainer;
import better.anticheat.core.util.ml.ModelConfig;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BetterAnticheat {

    @Getter
    private static BetterAnticheat instance;

    @Getter
    private final DataBridge dataBridge;
    private final Path directory;

    private boolean enabled;

    // Settings
    @Getter
    private int alertCooldown;
    @Getter
    private List<String> alertHover;
    @Getter
    private String alertMessage, alertPermission, clickCommand;
    @Getter
    private boolean punishmentModulo, testMode, useCommand;
    @Getter
    private Map<String, ModelConfig> modelConfigs = new HashMap<>();

    public BetterAnticheat(DataBridge dataBridge, Path directory) {
        this.dataBridge = dataBridge;
        this.directory = directory;
        this.enabled = true;

        instance = this;

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
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this.dataBridge));
        load();

        if (useCommand) {
            dataBridge.registerCommands(null, null, new BetterAnticheatCommand(this.dataBridge, this.directory));
        }
    }

    public void disable() {
        enabled = false;
    }

    public void load() {
        if (!enabled) return;

        dataBridge.logInfo("Beginning load!");

        ConfigSection settings = getFile("settings.yml", BetterAnticheat.class.getResourceAsStream("/settings.yml")).load();
        alertCooldown = settings.getObject(Integer.class, "alert-cooldown", 1000);
        alertPermission = settings.getObject(String.class, "alert-permission", "better.anticheat");
        alertHover = settings.getList(String.class, "alert-hover");
        alertMessage = settings.getObject(String.class, "alert-message", "");
        clickCommand = settings.getObject(String.class, "click-command", "");
        punishmentModulo = settings.getObject(Boolean.class, "punishment-modulo", true);
        testMode = settings.getObject(Boolean.class, "test-mode", false);
        useCommand = settings.getObject(Boolean.class, "enable-commands", true); // Default to true for people who have not updated their config.

        loadML(settings);

        CheckManager.load(this);
        PlayerManager.load(this);

        dataBridge.logInfo("Load finished!");
    }

    public void loadML(final ConfigSection baseConfig) {
        final var mlNode = baseConfig.getConfigSection("ml");
        final var mlEnabled = mlNode.getObject(Boolean.class, "enabled", false);

        final var models = mlNode.getConfigSection("models");

        if (mlEnabled) {
            for (final var child : models.getChildren()) {
                modelConfigs.put(child.getKey(), new ModelConfig(
                        child.getObject(String.class, "displayName", "example-model"),
                        child.getObject(String.class, "type", "model-type"),
                        child.getObject(Integer.class, "slice", 1),
                        child.getList(String.class, "legitDatasetNames"),
                        child.getList(String.class, "cheatDatasetNames"),
                        child.getObject(Boolean.class, "statistics", false),
                        child.getObject(Boolean.class, "shrink", true),
                        child.getObject(Integer.class, "samples", 10),
                        child.getObject(Integer.class, "threshold", 7),
                        child
                ));
            }
        }

        modelConfigs.forEach((name, config) -> {
            try {
                dataBridge.logInfo("Loading model for " + name + "...");
                final var model = MLTrainer.create(config.getLegitDatasetNames(), config.getCheatDatasetNames(), config.getType(), config.getSlice(), config.isStatistics(), config.isStatistics(), config.isShrink(), this.directory);
                config.setClassifierFunction(model);
                dataBridge.logInfo("Model for " + name + " loaded!");
            } catch (IOException e) {
                dataBridge.logInfo("Error while creating model trainer for " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public ConfigurationFile getFile(String name) {
        return new ConfigurationFile(name, directory);
    }

    public ConfigurationFile getFile(String name, InputStream input) {
        return new ConfigurationFile(name, directory, input);
    }
}