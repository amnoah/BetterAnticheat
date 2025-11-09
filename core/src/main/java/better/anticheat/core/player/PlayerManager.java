package better.anticheat.core.player;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.DataBridge;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This manager allows for us to properly map User, usernames, and entity IDs to Player objects. We handle player
 * addition, removal, and general management (like sending alerts) via this manager.
 */
public class PlayerManager {

    private final BetterAnticheat plugin;

    @Getter
    private final Map<User, Player> userMap = new ConcurrentHashMap<>();
    private final Int2ObjectMap<Player> idMap = new Int2ObjectRBTreeMap<>();
    private final List<Quantifier> quantifiers = new ArrayList<>();

    public PlayerManager(BetterAnticheat plugin) {
        this.plugin = plugin;
    }

    /*
     * Quantifier code.
     */

    /**
     * A quantifier allows for platform implementations to check certain things about the user before adding them to the
     * anticheat. As of writing this, this is being implemented to allow Bedrock players (via geyser) to be exempted.
     * For a user to be added to the anticheat, every Quantifier must return true.
     */
    public interface Quantifier {
        boolean check(User user);
    }

    /**
     * Register a quantifier to be evaluated by the anticheat for player additions.
     */
    public void registerQuantifier(Quantifier quantifier) {
        quantifiers.add(quantifier);
    }

    /*
     * Player management.
     */

    /**
     * Create a Player object for the given User and add it to our tracking maps.
     */
    public synchronized void addUser(User user, DataBridge dataBridge) {
        for (Quantifier quantifier : quantifiers) if (!quantifier.check(user)) return;
        Player player = new Player(plugin, user, dataBridge);
        userMap.put(user, player);
        idMap.put(user.getEntityId(), userMap.get(user));
    }

    /**
     * Remove the Player object associated with this User our tracking maps.
     */
    public synchronized void removeUser(User user) throws IOException {
        idMap.remove(user.getEntityId());
        final var removedPlayer = userMap.remove(user);
        if (removedPlayer == null) return;
        removedPlayer.close();
    }

    /*
     * Player getters.
     */

    /**
     * NOTE: getPlayerByUser should be used instead of this method. There are no plans for the removal of this method at
     * this time, but there are no guarantees.
     * Return the Player associated with the given User object.
     * Return null if not Player is associated.
     */
    @Deprecated
    public @Nullable Player getPlayer(User user) {
        return getPlayerByUser(user);
    }

    /**
     * Return the Player associated with the given entity id.
     * Return null if not Player is associated.
     */
    public @Nullable Player getPlayerByEntityId(int id) {
        return idMap.get(id);
    }

    /**
     * Return the Player associated with the given User object.
     * Return null if not Player is associated.
     */
    public @Nullable Player getPlayerByUser(User user) {
        return userMap.get(user);
    }

    /**
     * Return the Player associated with the given username.
     * Return null if not Player is associated.
     */
    public @Nullable Player getPlayerByUsername(String username) {
        for (final var value : userMap.values()) {
            if (value.getUser().getName().equalsIgnoreCase(username)) return value;
        }

        return null;
    }

    /*
     * Alert methods.
     */

    /**
     * Send the given component message to all players with alerts enabled.
     */
    public void sendAlert(Component text) {
        /*
         * We use the PacketEvents user collection as to include players who may be excluded via Quantifiers. Just
         * because a player may be logged in via something like Geyser doesn't have anything to do with whether they're
         * a staff member.
         */
        for (final var user : PacketEvents.getAPI().getProtocolManager().getUsers()) {
            // Do not send alerts to users who are not in the play state
            if (user.getEncoderState() != user.getDecoderState() || user.getConnectionState() != ConnectionState.PLAY) continue;
            final var player = getPlayer(user);
            /*
             * If we have a player object for the user, we can check if they have alerts enabled.
             * Otherwise, we just base it on their permissions.
             */
            if ((player == null && !plugin.getDataBridge().hasPermission(user, plugin.getAlertPermission()))
                    || (player != null && !player.isAlerts())) continue;
            user.sendMessage(text);
        }
    }

    /**
     * Send a component message to all players with verbose enabled.
     */
    public void sendVerbose(Component text) {
        // We do not use PacketEvents user collection here as we REQUIRE the player object to process this.
        for (final var player : userMap.values()) {
            if (!player.isVerbose() || player.getUser().getEncoderState() != player.getUser().getDecoderState() || player.getUser().getConnectionState() != ConnectionState.PLAY) continue;

            player.getUser().sendMessage(text);
        }
    }

    /*
     * Misc.
     */

    /**
     * Perform a reload for all players currently in the system.
     */
    public void load() {
        for (Player player : userMap.values()) player.load();
        plugin.getDataBridge().logInfo("Loaded checks for " + userMap.size() + " players.");
    }
}
