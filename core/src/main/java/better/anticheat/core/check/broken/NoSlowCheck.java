package better.anticheat.core.check.broken;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

@CheckInfo(name = "NoSlow", category = "combat")
public class NoSlowCheck extends Check {
    private boolean blocking = false, sprinting = false;

    public NoSlowCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This check is intended to detect starting sprinting while using an item (like blocking a shield, shooting a
         * bow, or eating food). However, this check has unexplainable falses.
         */

        switch (event.getPacketType()) {
            case USE_ITEM:
                blocking = true;
                sprinting = false;
                break;
            case ENTITY_ACTION:
                WrapperPlayClientEntityAction actionWrapper = new WrapperPlayClientEntityAction(event);
                if (actionWrapper.getAction().equals(WrapperPlayClientEntityAction.Action.START_SPRINTING)) {
                    sprinting = true;
                }
                break;
            case PLAYER_DIGGING:
                WrapperPlayClientPlayerDigging diggingWrapper = new WrapperPlayClientPlayerDigging(event);
                if (diggingWrapper.getAction() != DiggingAction.RELEASE_USE_ITEM) return;

                // Only flag on use item release as previously we don't know if the player is actually blocking.

                if (blocking && sprinting) fail();
                blocking = false;

                break;
        }
    }
}
