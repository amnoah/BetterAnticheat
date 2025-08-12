package better.anticheat.core.check.impl.flying;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;

/**
 * THis check looks for mathematically impossible position changes.
 */
@CheckInfo(
        name = "ArtificialPosition",
        category = "flying",
        requirements = {
                ClientFeatureRequirement.CLIENT_TICK_END
        }
)
public class ArtificialPositionCheck extends Check {

    private int ticks = -1, samePositions = 0;

    public ArtificialPositionCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * In the anticheat community, a common term you likely would have heard a few years back was "0.03". This is a
         * reference to a line of code in Minecraft that attempted to reduce the amount of packets sent to servers (and
         * thus bandwidth usage) by not sending arbitrarily small movements. The following is a recreation of that line:
         *
         * bool canSend = (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) > 0.0009
         *
         * This created a lot of issues for anticheats, though, as many movement checks are based on the movement in the
         * prior tick (Minecraft's movement works this way for applying gravity, friction, and other forces) and you may
         * have missed out on seeing what that movement was in the prior tick. The name "0.03" stuck for this issue as
         * if any one of the deltaX, deltaY, or deltaZ values were 0.03 and the others were 0 it would trigger.
         *
         * Today, the issue is not as prevalent for a few reasons:
         * 1. The client now sends its movement inputs, making movement predictions a lot easier.
         * 2. We now have an accurate indication of client ticking (the tick end packet), making it easy to identify
         * what ticks 0.03 is occurring (whenever a position isn't sent between tick end packets).
         * 3. The 0.0009 value was changed to 4.0E-8D (double representation in Java) in 1.18.2.
         *
         * I still have the opinion that this threshold should be set to 0 (no movement), but oh well! This is good
         * enough for now I guess.
         *
         * Anyway, this check is designed to abuse that. We know a position cannot be sent under this threshold unless
         * 20 ticks have passed without a position (see FlyingSequenceCheck), so we can verify that there is not more
         * than one movement under this threshold within a one-second span. This can help identify when cheats may fake
         * movements as many do not account for this threshold.
         */

        switch (event.getPacketType()) {
            case PLAYER_LOADED:
                ticks = samePositions = 0;
                break;

            case CLIENT_TICK_END:
                if (ticks < 0) break;
                if (++ticks == 20) ticks = samePositions = 0;
                break;
            case PLAYER_POSITION:
            case PLAYER_POSITION_AND_ROTATION:
                if (player.getTeleportTracker().isTeleported()) break;

                final double deltaX = player.getPositionTracker().getDeltaX();
                final double deltaY = player.getPositionTracker().getDeltaY();
                final double deltaZ = player.getPositionTracker().getDeltaZ();

                /*
                 * Value is (2.0E-4)^2 in 1.18.2+, (0.03)^2 in 1.18.1-.
                 */
                if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= 4.0E-8D) {
                    if (++samePositions > 1) fail("Excess: " + samePositions);
                }

                break;
        }
    }
}
