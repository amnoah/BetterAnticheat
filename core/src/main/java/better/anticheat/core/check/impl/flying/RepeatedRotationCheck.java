package better.anticheat.core.check.impl.flying;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;

/**
 * This check looks for rotation packets with no rotation changes.
 */
@CheckInfo(name = "RepeatedRotation", category = "flying", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class RepeatedRotationCheck extends Check {

    private boolean loaded = false;
    private int exemptTicks = 0;

    public RepeatedRotationCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * To send a pure rotation packet, you must, well, rotate. That is unless you're in a vehicle of course. But if
         * you don't rotate, you're faking the packet.
         */

        switch (event.getPacketType()) {
            case PLAYER_LOADED:
                loaded = true;
            case VEHICLE_MOVE:
                exemptTicks = 2;
                break;
            // Won't be effected by teleporting since that forces a POS_LOOK packet.
            case PLAYER_ROTATION:
                if (player.getTeleportTracker().isTeleported()) break;
                if (exemptTicks > 0) break;
                if (!loaded) return;
                if (player.getRotationTracker().getDeltaPitch() == 0 && player.getRotationTracker().getDeltaYaw() == 0) fail();
            case CLIENT_TICK_END:
                exemptTicks--;
                break;
        }
    }
}
