package better.anticheat.core.check.broken;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/*
 * NOTE: This check is currenlty not enabled due to false flag issues.
 * Use Item mechanics may have changed since this was originally designed?
 */
@CheckInfo(name = "RepeatedRelease", category = "dig")
public class RepeatedReleaseCheck extends Check {
    private boolean useItem = true;

    public RepeatedReleaseCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * For some reason you can send multiple releases? Needs debugging.
         */

        switch (event.getPacketType()) {
            //Keep track of whether the player sent an initial use item packet.
            case USE_ITEM:
                useItem = true;

                break;
            case PLAYER_DIGGING:
                WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

                /*
                 * In 1.9 and above using an item works as the following:
                 * First, the client initiates it by sending a use item packet.
                 * Second, the client releases it by sending a block dig packet.
                 *
                 * This means that between all release use item digs there must be a use item.
                 * From my testing, it does not appear like there must be a release between uses as there are edge
                 * cases that make UseItem seem to de-sync.
                 */
                if (wrapper.getAction() == DiggingAction.RELEASE_USE_ITEM) {
                    if (!useItem) fail();

                    useItem = false;
                }

                break;
        }
    }
}
