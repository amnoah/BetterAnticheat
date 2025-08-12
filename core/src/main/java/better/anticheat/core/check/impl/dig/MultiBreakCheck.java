package better.anticheat.core.check.impl.dig;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/**
 * This check looks for multiple blocks being dug at once.
 */
@CheckInfo(name = "MultiBreak", category = "dig")
public class MultiBreakCheck extends Check {

    private boolean hasStarted = false;
    private Vector3i latestStartPosition;

    public MultiBreakCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This check patches a very interesting bypass mechanic in some cheats.
         *
         * Some clients bypass classic fast break checks by following this process:
         * Start1 -> Start2 -> Finish1 -> Start3 -> Finish2 -> Start4 -> etc
         *
         * It starts digging multiple blocks at the same time, waiting until the proper time has passed to finish any.
         * This also isn't easily detectable by checking if the last action and current action are starts because the
         * vanilla client does that when breaking insta-break blocks. This doesn't seem to be as common anymore, but I'm
         * still including it regardless.
         *
         * Interesting note for 1.21+! I've noticed behavior where if a block is next to an insta-break block in a
         * WorldGuard protected area, you can mine the block through the insta-break. The client will only ever Start
         * digging the insta-break, but will finish the other block.
         */

        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

        switch (wrapper.getAction()) {
            case START_DIGGING:

                latestStartPosition = wrapper.getBlockPosition();
                hasStarted = true;
                break;
            case FINISHED_DIGGING:
                if (hasStarted) {
                    // Use Manhattan Distance to ensure blocks are within 1 block of the finished block.
                    int difX = Math.abs(wrapper.getBlockPosition().getX() - latestStartPosition.getX());
                    int difY = Math.abs(wrapper.getBlockPosition().getY() - latestStartPosition.getY());
                    int difZ = Math.abs(wrapper.getBlockPosition().getZ() - latestStartPosition.getZ());
                    if ((difX + difY + difZ) <= 1) return;
                    fail();
                }

                break;
        }
    }
}
