package better.anticheat.core.check.impl.dig;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/**
 * This check ensures dig packets are sent in the correct order.
 */
@CheckInfo(name = "DigOrder", category = "dig")
public class DigOrderCheck extends Check {

    private boolean started = true;

    public DigOrderCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This check used to also include a check for repeated Finish behavior - but that is now a feature in
         * Minecraft. The following is valid netcode:
         *
         * Start -> Stop -> Stop -> Stop -> Stop -> Stop
         *
         * This occurs when you break a block in a WorldGuard area with a block behind it. For some reason after
         * finishing breaking the block, when the block is replaced it is assumed that the Dig process is still
         * continuing. I'm really not sure why it works like this, but it means we can no longer check for repeated
         * finish actions. It also means the following is valid netcode:
         *
         * Start -> Stop -> Cancel
         *
         * Since the player is still continuing digging after finishing!!! I give up Mojang.
         */

        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        final WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

        switch (wrapper.getAction()) {
            case START_DIGGING:
                started = true;

                break;
            case CANCELLED_DIGGING:
                if (!started) fail();
                started = false;

                break;
        }
    }
}
