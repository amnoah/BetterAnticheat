package better.anticheat.core.check.impl.heuristic;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.util.MathUtil;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

/**
 * This check uses a magic acceleration value to attempt to find combat cheats.
 */
@CheckInfo(name = "CombatAcceleration", category = "heuristic", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class CombatAccelerationCheck extends Check {

    private boolean posRotChange = false, lastTickChange = false;
    private int ticksSinceAttack = 0;

    public CombatAccelerationCheck(BetterAnticheat plugin) {
        super(plugin);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * TODO: This check false flags and needs to be reworked. Find a more precise magic value or better exempts (VELOCITY/TELEPORT).
         *
         * This check uses a magic value (a value which doesn't have any real mathematical basis) to attempt to see if a
         * player's movement acceleration is too constant during combat with significant rotations. This was developed
         * via trial and error and should be used with caution due to its lack of true basis.
         */

        switch (event.getPacketType()) {
            case PLAYER_FLYING:
            case PLAYER_POSITION:
            case PLAYER_ROTATION:
            case PLAYER_POSITION_AND_ROTATION:
                WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);

                if (!wrapper.hasPositionChanged() || !wrapper.hasRotationChanged()) break;
                posRotChange = true;
                if (player.getTeleportTracker().isTeleported()) break;
                if (!lastTickChange) break;
                if (player.getPositionTracker().getDeltaXZ() <= 0.15) break;

                final double deltaYaw = Math.abs(player.getRotationTracker().getDeltaYaw());
                final double deltaPitch = Math.abs(player.getRotationTracker().getDeltaPitch());
                final double accelerationXZ = Math.abs(player.getPositionTracker().getDeltaXZ() -
                        player.getPositionTracker().getLastDeltaXZ());

                // Literally just a magic value. I'm not sure why it works so well, but it does.
                final double accelLimit = (deltaYaw / deltaPitch / 2000);
                if (accelerationXZ < accelLimit && deltaYaw > 15 && deltaPitch > 5 && ticksSinceAttack <= 2) {
                    fail("accelXZ=" + MathUtil.DF_EIGHT_PLACES.format(accelerationXZ) + 
                         " < accelLimit=" + MathUtil.DF_EIGHT_PLACES.format(accelLimit) + 
                         " | deltaYaw=" + MathUtil.DF_FOUR_PLACES.format(deltaYaw) + 
                         "° | deltaPitch=" + MathUtil.DF_FOUR_PLACES.format(deltaPitch) +
                         "° | ticksSinceAttack=" + ticksSinceAttack + 
                         " | deltaXZ=" + MathUtil.DF_SIX_PLACES.format(player.getPositionTracker().getDeltaXZ()) + 
                         " | lastDeltaXZ=" + MathUtil.DF_SIX_PLACES.format(player.getPositionTracker().getLastDeltaXZ()) + 
                         (player.getTeleportTracker().isTeleported() ? " | Teleported" : ""));
                }
                break;
            case INTERACT_ENTITY:
                ticksSinceAttack = 0;
                break;
            case CLIENT_TICK_END:
                lastTickChange = posRotChange;
                posRotChange = false;
                ticksSinceAttack = Math.min(5, ++ticksSinceAttack);
                break;
        }
    }
}