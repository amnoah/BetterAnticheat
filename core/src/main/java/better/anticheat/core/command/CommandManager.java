package better.anticheat.core.command;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.command.core.parameter.PlayerParameterType;
import better.anticheat.core.command.core.parameter.UserParameterType;
import better.anticheat.core.command.core.suggestion.PlayerNameSuggestionProvider;
import better.anticheat.core.command.impl.*;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.configuration.ConfigurationFile;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.protocol.player.User;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;

import java.util.*;

/**
 * This manager provides a centralized way to handle commands for players.
 */
public class CommandManager {

    private final BetterAnticheat plugin;
    private final List<Command> commands;
    private Lamp.Builder<CommandActor> builder;

    /**
     * Initialize the CommandManager object.
     */
    public CommandManager(BetterAnticheat plugin, Lamp.Builder<?> builder) {
        this.plugin = plugin;
        this.builder = (Lamp.Builder<CommandActor>) builder;

        /*
         * NOTE: Load order is important! Parent commands must be registered before their children!
         */
        commands = Arrays.asList(
                new BACCommand(plugin),
                new AlertsCommand(plugin),
                new VerboseCommand(plugin),
                new MitigateCommand(plugin),
                new RecordingCommand(plugin, plugin.getRecordingSaver()),
                new ReloadCommand(plugin)
        );
    }

    /**
     * Return a collection of all commands.
     */
    public Collection<Command> getAllCommands() {
        return Collections.unmodifiableList(commands);
    }


    /**
     * Load all commands via their preferred configuration files.
     */
    public Lamp<?> load() {
        if (plugin.getLamp() != null) {
            plugin.getLamp().unregisterAllCommands();
        }

        // Configure and build Lamp. We make sure to only suggest injected players and users.
        builder = builder.parameterTypes((config) -> {
                    config.addParameterType(Player.class, new PlayerParameterType());
                    config.addParameterType(User.class, new UserParameterType());
                }
        );
        builder = builder.suggestionProviders((config) -> {
            config.addProvider(Player.class, new PlayerNameSuggestionProvider());
            config.addProvider(User.class, new PlayerNameSuggestionProvider());
        });
        final var lamp = builder.build();

        int enabled = 0;
        for (Command command : commands) {
            ConfigurationFile file = plugin.getConfigurationManager().getConfigurationFile(command.getConfig());
            ConfigSection node = file.getRoot();
            node = node.getConfigSectionOrCreate(command.getName());
            command.load(node);
            if (command.isEnabled()) {
                enabled++;
                lamp.register(command.getOrphans().handler(command));
            }
        }

        plugin.getDataBridge().logInfo("Loaded " + commands.size() + " commands, with " + enabled + " being enabled.");

        return lamp;
    }
}
