package better.anticheat.core.check.impl.flying;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

/**
 * This check looks for artificial vehicle movement packets.
 */
@CheckInfo(name = "RepeatedSteer", category = "flying")
public class RepeatedSteerCheck extends Check {

    private boolean lastRotating = true;

    public RepeatedSteerCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This check works by the fact that between every two Vehicle Move packets the client sends a rotation. I'm
         * not exactly sure why this is since the Vehicle Move packets already includes rotation information, but that
         * works for us anyway!
         */

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            if (wrapper.hasRotationChanged()) lastRotating = true;
        } else if (event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE) {
            if (!lastRotating) fail();

            lastRotating = false;
        }
    }
}
