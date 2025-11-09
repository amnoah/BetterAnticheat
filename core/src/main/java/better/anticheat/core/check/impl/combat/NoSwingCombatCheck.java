package better.anticheat.core.check.impl.combat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

/**
 * This check looks for attacks without an animation packet sent.
 */
@CheckInfo(name = "NoSwingCombat", category = "combat")
public class NoSwingCombatCheck extends Check {

    private boolean swung = true;

    public NoSwingCombatCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * Arm animations must be sent for every entity attack.
         */

        switch (event.getPacketType()) {
            case ANIMATION:
                swung = true;

                break;
            case INTERACT_ENTITY:
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

                if (!swung) fail();

                swung = false;
                break;
        }
    }
}
