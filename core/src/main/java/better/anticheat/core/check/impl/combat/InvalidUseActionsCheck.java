package better.anticheat.core.check.impl.combat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;

/**
 * This check looks for attacking while using an item.
 */
@CheckInfo(name = "InvalidUseActions", category = "combat")
public class InvalidUseActionsCheck extends Check {

    private boolean blocking = false, attacked = false, placed = false;

    public InvalidUseActionsCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * In Minecraft, a pretty general rule is that you can't attack entities while using an item (pulling back a
         * bow, eating food, blocking a shield, etc). This sounds extremely simple to detect, until you start looking
         * into Minecraft's netcode. When using certain consumables like enderpearls, you send an initial use but never
         * release. This means that just because a player started using an item doesn't mean they're going to actually
         * be using it. So, we only know they were actually using an item when they release it. So our check likes this:
         * 1. We listen for an initial use item packet and start tracking at that point.
         * 2. We listen for action packets like block placements and attacks.
         * 3. On release, we see if they performed any actions and flag them if they did.
         */

        switch (event.getPacketType()) {
            case INTERACT_ENTITY:
                WrapperPlayClientInteractEntity interactWrapper = new WrapperPlayClientInteractEntity(event);
                if (interactWrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
                attacked = true;

                break;
            case PLAYER_BLOCK_PLACEMENT:
                placed = true;
                break;
            case USE_ITEM:

                blocking = true;
                attacked = placed = false;

                break;
            case PLAYER_DIGGING:
                WrapperPlayClientPlayerDigging diggingWrapper = new WrapperPlayClientPlayerDigging(event);
                if (diggingWrapper.getAction() != DiggingAction.RELEASE_USE_ITEM) return;

                if (blocking && attacked) fail("atk, act: " + diggingWrapper.getAction());
                else if (blocking && placed) fail("plc, act: " + diggingWrapper.getAction());
                blocking = attacked = placed = false;

                break;
        }
    }
}
