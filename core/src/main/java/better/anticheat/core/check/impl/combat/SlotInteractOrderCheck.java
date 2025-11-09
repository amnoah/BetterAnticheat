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

    private boolean attacked = false;
    //private boolean slotChange = false;

    public SlotInteractOrderCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * Minecraft's networking will send packets in a certain order within the tick. One notable order that some
         * cheats often break is that slot change packets must be sent before attack packets.
         */

        switch (event.getPacketType()) {
            case CLIENT_TICK_END -> attacked = false;
            case INTERACT_ENTITY -> attacked = true;
            case HELD_ITEM_CHANGE -> {
                if (attacked) fail();
            }
        }
    }
}
