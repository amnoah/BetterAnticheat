package better.anticheat.core.check.broken;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;

import java.util.ArrayList;
import java.util.List;

@CheckInfo(name = "TeleportConfirmOrder", category = "flying")
public class TeleportConfirmOrderCheck extends Check {
    private final List<Integer> teleportIDs = new ArrayList<>();

    public TeleportConfirmOrderCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.TELEPORT_CONFIRM) return;
        WrapperPlayClientTeleportConfirm wrapper = new WrapperPlayClientTeleportConfirm(event);

        if (!teleportIDs.isEmpty() && teleportIDs.getFirst() == wrapper.getTeleportId()) teleportIDs.removeFirst();
        else fail();
    }

    @Override
    public void handleSendPlayPacket(PacketPlaySendEvent event) {
        if (event.isCancelled()) return;
        if (event.getPacketType() != PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) return;
        WrapperPlayServerPlayerPositionAndLook wrapper = new WrapperPlayServerPlayerPositionAndLook(event);
        teleportIDs.add(wrapper.getTeleportId());
    }
}
