package better.anticheat.core.check.impl.flying;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

/**
 * This check looks for wrongful rotation values.
 */
@CheckInfo(name = "ImpossibleRotation", category = "flying")
public class ImpossibleRotationCheck extends Check {

    public ImpossibleRotationCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * The vanilla client CANNOT send numbers that are not finite for its rotation values.
         * The pitch rotation is also capped between -90 and 90.
         */

        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
        if (!wrapper.hasRotationChanged()) return;

        final boolean invalidPitch = checkForInvalid(wrapper.getLocation().getPitch(), true);
        final boolean invalidYaw = checkForInvalid(wrapper.getLocation().getYaw(), false);

        if (invalidPitch || invalidYaw) fail();
    }

    private boolean checkForInvalid(float rotation, boolean checkPitch) {
        if (!Float.isFinite(rotation)) return true;
        else return (checkPitch && Math.abs(rotation) > 90);
    }
}
