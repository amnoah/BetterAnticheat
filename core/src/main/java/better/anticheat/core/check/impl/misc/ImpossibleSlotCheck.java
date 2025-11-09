package better.anticheat.core.check.impl.misc;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;

/**
 * This check looks for impossible slot values.
 */
@CheckInfo(name = "ImpossibleSlot", category = "misc")
public class ImpossibleSlotCheck extends Check {

    public ImpossibleSlotCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * Minecraft's hotbar slots are numbered 0-8 internally. Any value outside of these is physically impossible to
         * send.
         */

        if (event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE) return;
        final WrapperPlayClientHeldItemChange wrapper = new WrapperPlayClientHeldItemChange(event);
        final int slot = wrapper.getSlot();

        if (slot < 0 || slot > 8) fail();
    }
}
