package better.anticheat.core.check.impl.place;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;

/**
 * This check looks for invalid raycasts.
 */
@CheckInfo(name = "CursorPosition", category = "place")
public class CursorPositionCheck extends Check {

    public CursorPositionCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * One really helpful piece of information that the client provides when placing blocks is a raycast indicating
         * where on the block the player was aiming at. This is used to tell the server how to place things like stairs,
         * slabs, chains, scaffolding, etc.
         *
         * This raycast must stay within of the bounds of 0 <= i <= 1, with i representing the raycast value. This means
         * that values below 0 or above 1 are invalid. While largely patched today, a large issue with cheats in the
         * past was that they would artificially place blocks in spots that they cannot see and then still perform the
         * required raycast, essentially reporting on themselves that they weren't actually able to see the block that
         * they placed.
         */

        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);

        if (wrapper.getFace().equals(BlockFace.OTHER)) return;
        if (outOfBounds(wrapper.getCursorPosition().getX())) fail("x " + wrapper.getCursorPosition().getX());
        if (outOfBounds(wrapper.getCursorPosition().getY())) fail("y " + wrapper.getCursorPosition().getY());
        if (outOfBounds(wrapper.getCursorPosition().getZ())) fail("z " + wrapper.getCursorPosition().getZ());
    }

    private boolean outOfBounds(float i) {
        return 0 > i || i > 1;
    }
}
