package better.anticheat.core.check.impl.chat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;

/**
 * This check looks for impossible chat messages.
 */
@CheckInfo(name = "ImpossibleMessage", category = "chat")
public class ImpossibleMessageCheck extends Check {

    public ImpossibleMessageCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This check looks for messages that are either fully empty or only made up of spacing characters.
         */

        if (event.getPacketType() != PacketType.Play.Client.CHAT_MESSAGE) return;
        WrapperPlayClientChatMessage wrapper = new WrapperPlayClientChatMessage(event);
        String message = wrapper.getMessage();

        // You cannot send empty messages or messages starting with spaces.
        if (message.trim().isEmpty()) fail("emp");
        else if (message.charAt(0) == ' ') fail("spc");
    }
}
