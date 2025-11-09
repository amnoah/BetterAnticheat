package better.anticheat.core.check.broken;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

@CheckInfo(name = "NoSwingDig", category = "dig", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class NoSwingDigCheck extends Check {
    private boolean started = false, swungThisTick = false, constantSwinging = false;

    public NoSwingDigCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This check just verifies that you swing once a tick during digging.
         *
         * It is entirely stable, except for one very specific scenario!
         *
         * Currently, it is not stable due to some really weird behavior with world borders.
         * If you start digging a block near the world border and then adjust your view to look at the blue barrier, you
         * can enter a state where your latest digging status is still start, but you are no longer swinging. You can
         * then either let go and have this check flag via the Cancel action or return to the block and have it flag via
         * the Finished action.
         *
         * Potential ideas for fixing this revolve around compensating the world and checking for proximity to the world
         * border.
         */

        switch (event.getPacketType()) {
            case CLIENT_TICK_END:
                if (started && !swungThisTick) constantSwinging = false;
                swungThisTick = false;
                break;
            case ANIMATION:

                if (started) swungThisTick = true;

                break;
            case PLAYER_DIGGING:
                WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

                // Switches > if statements.
                switch (wrapper.getAction()) {
                    case START_DIGGING:

                        started = true;
                        constantSwinging = true;

                        break;
                    case FINISHED_DIGGING:
                    case CANCELLED_DIGGING:

                        // If a player reaches the end of digging they must have been actively swinging.
                        if (started && !constantSwinging) fail();
                        started = false;

                        break;
                }

                break;
        }
    }
}
