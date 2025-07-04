package better.anticheat.core.check.impl.dig;

import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckInfo(name = "DigBlockFacePosition", category = "dig", config = "checks")
public class DigBlockFacePositionCheck extends Check {

    private Vector3d position = null;

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            if (!wrapper.hasPositionChanged()) return;
            position = wrapper.getLocation().getPosition();
            return;
        } else if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        WrapperPlayClientPlayerDigging digWrapper = new WrapperPlayClientPlayerDigging(event);
        switch (digWrapper.getAction()) {
            case FINISHED_DIGGING:
            case START_DIGGING:
                break;
            default:
                return;
        }

        Vector3i blockPos = digWrapper.getBlockPosition();
        switch (digWrapper.getBlockFace()) {
            case OTHER:
                if (blockPos.getX() != -1 || blockPos.getY() != 4095 || blockPos.getZ() != -1) fail("other");
                break;
            case NORTH:
                if ((blockPos.getZ() + 1.03) < position.getZ()) fail(digWrapper.getBlockFace() + " " + digWrapper.getAction());
                break;
            case SOUTH:
                if ((blockPos.getZ() - 0.03) > position.getZ()) fail(digWrapper.getBlockFace() + " " + digWrapper.getAction());
                break;
            case WEST:
                if ((blockPos.getX() + 1.03) < position.getX()) fail(digWrapper.getBlockFace() + " " + digWrapper.getAction());
                break;
            case EAST:
                if ((blockPos.getX() - 0.03) > position.getX()) fail(digWrapper.getBlockFace() + " " + digWrapper.getAction());
                break;
            case DOWN:
                if ((position.getY() - blockPos.getY()) >= 1) fail(digWrapper.getBlockFace() + " " + digWrapper.getAction());
                break;
        }
    }
}