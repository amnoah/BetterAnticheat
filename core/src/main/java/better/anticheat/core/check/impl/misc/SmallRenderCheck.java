package better.anticheat.core.check.impl.misc;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;

/**
 * This check looks for excessively small render distance values.
 */
@CheckInfo(name = "SmallRender", category = "misc")
public class SmallRenderCheck extends Check {

    public SmallRenderCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * In modern clients, the lowest value that the render distance can be set to without modification is 2.
         * There's some issue right now with the Settings wrapper throwing exceptions. This try catch is a quick patch
         * until that can be investigated fully.
         */

        if (event.getPacketType() != PacketType.Play.Client.CLIENT_SETTINGS) return;
        try {
            WrapperPlayClientSettings wrapper = new WrapperPlayClientSettings(event);
            if (wrapper.getViewDistance() < 2) fail();
        } catch (final Exception e) {
            return;
        }
    }
}
