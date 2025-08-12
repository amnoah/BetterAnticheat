package better.anticheat.core.check.broken;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;

import java.util.ArrayList;
import java.util.List;

public class PingPongOrderCheck extends Check {

    private final List<Integer> transactionIDs = new ArrayList<>();

    public PingPongOrderCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }


    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PONG) return;
        WrapperPlayClientPong wrapper = new WrapperPlayClientPong(event);

        if (!transactionIDs.isEmpty() && transactionIDs.getFirst() == wrapper.getId()) transactionIDs.removeFirst();
        else fail();
    }

    @Override
    public void handleSendPlayPacket(PacketPlaySendEvent event) {
        if (event.isCancelled()) return;
        if (event.getPacketType() != PacketType.Play.Server.PING) return;
        WrapperPlayServerPing wrapper = new WrapperPlayServerPing(event);
        transactionIDs.add(wrapper.getId());
    }
}
