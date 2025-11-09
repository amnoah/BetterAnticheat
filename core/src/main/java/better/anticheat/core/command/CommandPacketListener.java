package better.anticheat.core.command;

import better.anticheat.core.BetterAnticheat;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.chat.Node;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDeclareCommands;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import wtf.spare.sparej.Pair;

import java.util.*;

/**
 * As of writing this, BetterAnticheat is designed to utilize Lamp for command registration. One major flaw of Lamp is
 * that there is no way to adjust command declarations dynamically at runtime based on configurable permissions. So, to
 * hide commands which users have no permission to see, we bypass Lamp entirely and modify the Command Declaration
 * packet to ensure the player never sees a command they shouldn't see.
 */
public class CommandPacketListener extends SimplePacketListenerAbstract {

    private final BetterAnticheat plugin;

    public CommandPacketListener(BetterAnticheat plugin) {
        this.plugin = plugin;
    }

    /*
     * The only limitation of this current implementation is that it does not truly remove Command Declarations, rather
     * just removing any sort of useful information from them. This leaves floating command declarations in the tree
     * that a truly advanced client could look for to try to identify BAC at work. However, the client would have to
     * guess as there is nothing that indicates BAC is responsible for this rather than any other plugin.
     *
     * Fully removing nodes from the tree would require parsing every node to ensure any references to other nodes is
     * recalculated as their position may have shifted during modification. This does not sound worthwhile, personally,
     * given the fact that we already have a large amount of obscurity gained by this system.
     */

    /**
     * Handle base logic for the Declare Commands packet.
     */
    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.DECLARE_COMMANDS) return;
        WrapperPlayServerDeclareCommands wrapper = new WrapperPlayServerDeclareCommands(event);

        // Find the root node. This should typically be at index 0, but isn't guaranteed.
        Node root = null;
        for (Node node : wrapper.getNodes()) {
            if ((node.getFlags() & 0x03) == 0) {
                root = node;
                break;
            }
        }

        // Improperly formatted packet. Don't continue.
        if (root == null) return;

        // Cycle through all declarations and our commands, removing declarations the player shouldn't be able to access.
        Set<Pair<Node, Integer>> parentRemovals = new ObjectArraySet<>();
        for (int i : root.getChildren()) {
            for (Command command : plugin.getCommandManager().getAllCommands()) {
                // Only check root commands.
                if (command.parent != null) continue;
                validateCommandMatch(parentRemovals, event.getUser(), command, wrapper, i, root);
            }
        }

        /*
         * Remove commands that are removed from their parent. This prevents the client from running into errors when
         * attempting to parse a command tree.
         * The only other issue I can think of is that the command tree may get screwed up if a command has a redirect
         * set to a node that we removed (which shouldn't be possible since other plugins shouldn't know about our
         * commands). But, luckily, I don't think Lamp is designed well enough to utilize this feature anyway.
         */
        for (Pair<Node, Integer> removal : parentRemovals) removal.getX().getChildren().remove(removal.getY());
    }

    /**
     * Recursively verify if declared commands are able to be run by the user, and remove their declaration if the user
     * cannot use the command.
     */
    private void validateCommandMatch(Set<Pair<Node, Integer>> parentRemovals, User user, Command command, WrapperPlayServerDeclareCommands wrapper, int index, Node parent) {
        // If a command isn't enabled, it won't be registered by Lamp whatsoever. So, it'd be a waste to process it.
        if (!command.isEnabled()) return;
        Node node = wrapper.getNodes().get(index);

        // Permissions only exist for literal commands, not suggestions.
        if ((node.getFlags() & 0x03) != 1) return;

        // Should never occur.
        if (node.getName().isEmpty()) return;

        // Check if any name associated with the command matches this declaration.
        String declaredCommand = node.getName().get();
        boolean matched = false;
        for (String commandName : command.getNames()) {
            if (commandName.equalsIgnoreCase(declaredCommand)) {
                matched = true;
                break;
            }
        }
        if (!matched) return;

        // If the user doesn't have permission for this command, remove the declaration.
        if (!command.hasPermission(user)) {
            parentRemovals.add(new Pair<>(parent, index));
            removeNodeAndAllChildren(wrapper, index);
            return;
        }

        // If the user has permission for this command, verify they have permission for its children.
        for (int i : node.getChildren()) {
            for (Command childCommand : command.getChildren()) {
                validateCommandMatch(parentRemovals, user, childCommand, wrapper, i, node);
            }
        }
    }

    /**
     * Recursively remove all children from the declaration. We don't want any nodes hanging around in there that
     * clients may be able to parse to discover the anticheat.
     */
    private void removeNodeAndAllChildren(WrapperPlayServerDeclareCommands wrapper, int index) {
        Node node = wrapper.getNodes().get(index);
        node.setName(Optional.of(""));
        for (Integer child : node.getChildren()) removeNodeAndAllChildren(wrapper, child);
        node.getChildren().clear();
    }
}
