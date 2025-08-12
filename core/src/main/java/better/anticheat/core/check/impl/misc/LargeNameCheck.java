package better.anticheat.core.check.impl.misc;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;

/**
 * This check looks for excessively long names on items.
 */
@CheckInfo(name = "LargeName", category = "misc")
public class LargeNameCheck extends Check {

    public LargeNameCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        // As of 1.17, the maximum size for an item name is 50.

        if (event.getPacketType() != PacketType.Play.Client.NAME_ITEM) return;
        final WrapperPlayClientNameItem wrapper = new WrapperPlayClientNameItem(event);

        if (wrapper.getItemName().length() > 50) fail();
    }
}