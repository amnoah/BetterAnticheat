package better.anticheat.core.check.impl.dig;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/**
 * This check looks for multiple dig packets in a tick.
 */
@CheckInfo(name = "RepeatedDig", category = "dig", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class RepeatedDigCheck extends Check {

    private boolean dug = false;

    public RepeatedDigCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This client cannot send multiple start and stop dig actions within a single tick.
         */

        switch (event.getPacketType()) {
            case CLIENT_TICK_END:
                dug = false;
                break;
            case PLAYER_DIGGING:
                WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

                switch (wrapper.getAction()) {
                    case START_DIGGING:
                    case FINISHED_DIGGING:
                        break;
                    default:
                        return;
                }

                if (dug) fail(wrapper.getAction());
                dug = true;

                break;
        }
    }
}
