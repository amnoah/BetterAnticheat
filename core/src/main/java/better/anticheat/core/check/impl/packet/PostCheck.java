package better.anticheat.core.check.impl.packet;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

import java.util.ArrayList;
import java.util.List;

/**
 * This check looks for packets sent at the wrong stage of a tick.
 */
@CheckInfo(
        name = "Post",
        category = "packet",
        requirements = {
                ClientFeatureRequirement.CLIENT_TICK_END
        }
)
public class PostCheck extends Check {

    private boolean sentFlying = false, held = true, login = false;
    private final List<PacketType.Play.Client> post = new ArrayList<>();

    public PostCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * In Minecraft networking, certain packets are sent at certain stages of the tick. The two most important are
         * the Flying packet (Flying, Position, Rotation, and Position and Rotation) and the End Tick packet. The flying
         * packet and its subtypes are sent near the end of the tick, while the End Tick packet is sent at the absolute
         * end of the tick. Thus, we can check if various types of packets are sent between the Flying and End Tick
         * packet when they should be sent earlier in the tick. This can be indicative of a cheat artificially sending
         * packets when it shouldn't be.
         *
         * This also includes a patch for the change slot packet where the first one may be sent out of order.
         */

        switch (event.getPacketType()) {
            case PLAYER_LOADED:
                login = true;
                break;
            case PLAYER_FLYING:
            case PLAYER_POSITION:
            case PLAYER_ROTATION:
            case PLAYER_POSITION_AND_ROTATION:
                post.clear();
                sentFlying = true;
                break;
            case CLIENT_TICK_END:
                if (sentFlying && !post.isEmpty()) {
                    fail(post);
                }
                sentFlying = false;
                post.clear();
                break;
            case HELD_ITEM_CHANGE:
                if (held) {
                    held = false;
                    break;
                }
            case ANIMATION:
            case PLAYER_INPUT:
            case ENTITY_ACTION:
            case CLOSE_WINDOW:
            case CREATIVE_INVENTORY_ACTION:
            case EDIT_BOOK:
            case INTERACT_ENTITY:
            case NAME_ITEM:
            case PLAYER_ABILITIES:
            case PLAYER_BLOCK_PLACEMENT:
            case PLAYER_DIGGING:
            case PLUGIN_MESSAGE:
            case SPECTATE:
            case TAB_COMPLETE:
            case UPDATE_SIGN:
                if (!login) break;
                if (sentFlying) post.add(event.getPacketType());
                break;
        }
    }
}
