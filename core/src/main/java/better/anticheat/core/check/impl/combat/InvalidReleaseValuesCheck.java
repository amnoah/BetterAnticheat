package better.anticheat.core.check.impl.combat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/**
 * This check looks for improperly filled release packets.
 */
@CheckInfo(name = "InvalidReleaseValues", category = "combat")
public class InvalidReleaseValuesCheck extends Check {

    public InvalidReleaseValuesCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * Release packets have defined inputs that can NEVER be deviated from. Some auto block and item cheats
         * improperly assume values and mess this up. These are what the values should be:
         * BlockFace = DOWN
         * X = 0
         * Y = 0
         * Z = 0
         */

        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

        if (wrapper.getAction().equals(DiggingAction.RELEASE_USE_ITEM)) {

            if (
                    !wrapper.getBlockFace().equals(BlockFace.DOWN) ||
                    wrapper.getBlockPosition().getX() != 0 ||
                    wrapper.getBlockPosition().getY() != 0 ||
                    wrapper.getBlockPosition().getZ() != 0
            ) fail();
        }
    }
}
