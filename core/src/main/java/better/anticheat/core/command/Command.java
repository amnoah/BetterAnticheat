package better.anticheat.core.command;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import lombok.Getter;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.orphan.OrphanCommand;
import revxrsal.commands.orphan.Orphans;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This monstrosity is the result of me disliking annotation-based command frameworks while using one!
 * This essentially turns Lamp into an object-based command system that includes support for layered subcommands,
 * config generation/handling, permission processing, and run-time name and permission resolution. If it weren't for the
 * fact that Bukkit sucks for listening to commands in the pre-process stage, this likely would've been avoided with the
 * sharkbyte packet-based command system.
 * <p>
 * When using the Command class there are a few core things to understand:
 * 1. To use the constructor with only BetterAnticheat, you must use the @CommandInfo annotation on the class.
 * 2. The command handling system is that from Lamp. Their wiki will be your best help.
 * 3. If you override the config handling, you must super.load()!
 */
@Getter
public abstract class Command implements OrphanCommand {

    // Fallback kyori serializer
    protected static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder().hexColors().extractUrls().build();

    protected final BetterAnticheat plugin;

    // Annotation parameters.
    protected final String name, config;
    protected final Class<? extends Command> parentClass;
    protected Command parent = null;

    // Config options.
    private boolean enabled;
    private String[] names, paths, permissions;
    protected List<String> defaultNames = new ArrayList<>(), defaultPermissions = new ArrayList<>();

    //private String[] paths;
    private Orphans orphans;
    private final List<Command> children = new ArrayList<>();

    /**
     * Construct the command via info provided in CommandInfo annotation.
     * This is the recommended approach but requires a @CommadInfo annotation on implementations.
     */
    public Command(BetterAnticheat plugin) {
        this.plugin = plugin;

        CommandInfo info = this.getClass().getAnnotation(CommandInfo.class);
        if (info == null) throw new InvalidParameterException("No CommandInfo annotation!");

        // Copy values from annotation and attempt to find an object of our parent.
        name = info.name();
        defaultNames.add(name);
        config = info.config();
        parentClass = info.parent();
        defaultNames.addAll(Arrays.asList(info.aliases()));
    }

    /**
     * Construct the command via parameters.
     */
    public Command(BetterAnticheat plugin, String name, String config, Class<? extends Command> parent, String... aliases) {
        this.plugin = plugin;
        this.name = name;
        defaultNames.add(name);
        this.config = config;
        this.parentClass = parent;
        defaultNames.addAll(Arrays.asList(aliases));
    }

    /**
     * Convert a given CommandActor to BetterAnticheat Player.
     */
    protected @Nullable Player getPlayerFromActor(final CommandActor actor) {
        return BetterAnticheat.getInstance().getPlayerManager().getPlayerByUsername(actor.name());
    }

    /**
     * Convert a given CommandActor to PacketEvents User.
     */
    protected @Nullable User getUserFromActor(final CommandActor actor) {
        for (User user : PacketEvents.getAPI().getProtocolManager().getUsers()) {
            if (user.getName() == null || !user.getName().equalsIgnoreCase(actor.name())) continue;
            return user;
        }
        return null;
    }

    /**
     * Return whether a given CommandActor has any of this command's permissions.
     */
    public boolean hasPermission(final CommandActor actor) {
        if (actor.name().equalsIgnoreCase("console")) return true;
        return hasPermission(getUserFromActor(actor));
    }

    /**
     * Return whether a given User has any of this command's permissions.
     */
    public boolean hasPermission(final User user) {
        if (user == null) return false;
        return plugin.getDataBridge().hasPermission(user, permissions);
    }

    /**
     * Send the given component back to the given actor.
     */
    protected void sendReply(final CommandActor actor, final ComponentLike message) {
        try {
            /*
             * All Lamp implementations include this method but only begin to include them in their interfaces once we
             * get to platform-specific implementations??? I'd love for reflection to not be essential for something so
             * basic, but I'm beefing with Lamp.
             */
            final var method = actor.getClass().getMethod("reply", ComponentLike.class);
            method.trySetAccessible();
            method.invoke(actor, message);
        } catch (final Exception e) {
            plugin.getDataBridge().logWarning("Failed to find reply method, is your server up to date? " + e);
            actor.reply(LEGACY_COMPONENT_SERIALIZER.serialize(message.asComponent()));
        }
    }

    /**
     * Load default settings for the command and process them.
     */
    public void load(ConfigSection section) {
        if (section == null) {
            enabled = false;
            return;
        }

        // Attempt to lazy load parent command.;
        if (parent == null && parentClass != null) {
            for (Command command : plugin.getCommandManager().getAllCommands()) {
                if (!command.getClass().equals(parentClass)) continue;
                parent = command;
                parent.children.add(this);
            }
        }

        enabled = section.getOrSetBoolean("enabled", true);
        // Cannot be enabled if parent commands are disabled.
        Command par = parent;
        while (par != null) {
            if (!par.isEnabled()) {
                enabled = false;
                break;
            }
            par = par.getParent();
        }

        // No use in wasting more time loading.
        if (!enabled) return;

        // Grab lists.
        List<String> namesList = section.getOrSetStringList("names", defaultNames);
        names = new String[namesList.size()];
        for (int i = 0; i < namesList.size(); i++) names[i] = namesList.get(i);
        List<String> permissionsList = section.getOrSetStringList("permissions", Arrays.asList(
                "better.anticheat." + name.toLowerCase(),
                "example.permission.node"
        ));
        permissions = new String[permissionsList.size()];
        for (int i = 0; i < permissionsList.size(); i++) permissions[i] = permissionsList.get(i);

        // Process changes.

        if (parent == null) {
            paths = names;
        } else {
            paths = new String[parent.paths.length * names.length];
            int i = 0;
            for (String parentPath : parent.paths) {
                for (String childPath : names) {
                    paths[i] = parentPath + " " + childPath;
                    i++;
                }
            }
        }
        orphans = Orphans.path(paths);
    }
}