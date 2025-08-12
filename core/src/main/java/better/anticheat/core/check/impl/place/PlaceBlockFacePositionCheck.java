package better.anticheat.core.check.impl.place;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

/**
 * This check looks to see if a block is placed against a block that is mathematically impossible to see.
 */
@CheckInfo(name = "PlaceBlockFacePosition", category = "place")
public class PlaceBlockFacePositionCheck extends Check {

    private Vector3d position = null;

    public PlaceBlockFacePositionCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * When working with block placements, one important insight that the client informs the server of is the side
         * of the block that was interacted with. Using this information, we can use the player's position to determine
         * whether it was physically possible for the player to have seen the side that they placed a block against.
         *
         * This also includes a horrifically simple patch for 2.0E-4 (see ArtificialPositionCheck for an explanation of
         * what that means) where we just give that much leniency for any interaction. If it works, it works!
         */

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            if (!wrapper.hasPositionChanged()) return;
            position = wrapper.getLocation().getPosition();
            return;
        } else if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;

        WrapperPlayClientPlayerBlockPlacement placeWrapper = new WrapperPlayClientPlayerBlockPlacement(event);
        Vector3i blockPos = placeWrapper.getBlockPosition();
        switch (placeWrapper.getFace()) {
            case OTHER:
                if (blockPos.getX() != -1 || blockPos.getY() != 4095 || blockPos.getZ() != -1) fail("other");
                break;
            case NORTH:
                if ((blockPos.getZ() + 1 + 2.0E-4) < position.getZ()) fail(placeWrapper.getFace());
                break;
            case SOUTH:
                if ((blockPos.getZ() - 2.0E-4) > position.getZ()) fail(placeWrapper.getFace());
                break;
            case WEST:
                if ((blockPos.getX() + 1 + 2.0E-4) < position.getX()) fail(placeWrapper.getFace());
                break;
            case EAST:
                if ((blockPos.getX() - 2.0E-4) > position.getX()) fail(placeWrapper.getFace());
                break;
            case DOWN:
                if (position.getY() >= blockPos.getY()) fail();
                break;
        }
    }
}
