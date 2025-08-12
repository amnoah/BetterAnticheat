package better.anticheat.core.check.impl.chat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;

/**
 * This check looks for chatting with no chat box.
 */
@CheckInfo(name = "HiddenChat", category = "chat")
public class HiddenChatCheck extends Check {

    private boolean canChat = true, initalFlag = false;

    public HiddenChatCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * In Minecraft, there are three chat modes:
         * FULL - Full access to the chat with other players.
         * SYSTEM - You should only be able to interact with the Minecraft server, not other players.
         * HIDDEN - You cannot interact with anything.
         * So, if a player interacts with the chat on Hidden, they're cheating!
         */

        switch (event.getPacketType()) {
            case CLIENT_SETTINGS:
                try {
                    WrapperPlayClientSettings wrapper = new WrapperPlayClientSettings(event);
                    switch (wrapper.getChatVisibility()) {
                        case FULL:
                        case SYSTEM:
                            canChat = true;
                            break;
                        case HIDDEN:
                            canChat = false;
                            break;
                    }
                } catch (final Exception e) {
                    setEnabled(false);
                    return;
                }
                break;
            case CHAT_MESSAGE:
                if (!canChat) {
                    // Settings aren't always sent to update this until after chats are sent.
                    if (initalFlag) fail();
                    else initalFlag = true;
                }
                break;
        }
    }
}