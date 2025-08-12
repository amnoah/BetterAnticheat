package better.anticheat.core.check.impl.flying;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

/**
 * This check looks for wrongful position values.
 */
@CheckInfo(name = "ImpossiblePosition", category = "flying")
public class ImpossiblePositionCheck extends Check {

    public ImpossiblePositionCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * The vanilla client CANNOT send numbers that are not finite for its position values.
         */

        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasPositionChanged()) return;

        if (!Double.isFinite(wrapper.getLocation().getX())) fail("x");
        if (!Double.isFinite(wrapper.getLocation().getY())) fail("y");
        if (!Double.isFinite(wrapper.getLocation().getZ())) fail("z");
    }
}
