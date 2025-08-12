package better.anticheat.core.check.impl.combat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;

/**
 * This check looks for the order of slot change and interact entity packets.
 */
@CheckInfo(name = "SlotInteractOrder", category = "combat", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class SlotInteractOrderCheck extends Check {

    private boolean slotChange = false;

    public SlotInteractOrderCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * Minecraft's networking will send packets in a certain order within the tick. One notable order that some
         * cheats often break is that attack packets must be sent before slot change packets.
         */

        switch (event.getPacketType()) {
            case CLIENT_TICK_END:
                slotChange = false;
                break;
            case HELD_ITEM_CHANGE:
                slotChange = true;
                break;
            case INTERACT_ENTITY:
                if (slotChange) fail();
                break;
        }
    }
}
