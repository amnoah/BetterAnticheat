package better.anticheat.core.check.impl.combat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * This check looks for the order of entity action and interact entity packets.
 */
@CheckInfo(name = "ActionInteractOrder", category = "combat", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class ActionInteractOrderCheck extends Check {

    private boolean sentEntityAction = false;

    public ActionInteractOrderCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * Minecraft's networking will send packets in a certain order within the tick. One notable order that some
         * cheats often break is that attack packets must be sent before entity action packets.
         */

        switch (event.getPacketType()) {
            case CLIENT_TICK_END:
                sentEntityAction = false;
                break;
            case ENTITY_ACTION:
                sentEntityAction = true;
                break;
            case INTERACT_ENTITY:
                // Only run when the player is attacking and when they're actively sending flying packets.
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                if (!wrapper.getAction().equals(WrapperPlayClientInteractEntity.InteractAction.ATTACK)) return;

                // You cannot update an entity action status before you attack.
                if (sentEntityAction) fail();
                break;
        }
    }
}
