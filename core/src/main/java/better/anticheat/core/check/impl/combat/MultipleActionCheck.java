package better.anticheat.core.check.impl.combat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

/**
 * This check looks for multiple actions within a tick.
 */
@CheckInfo(name = "MultipleAction", category = "combat", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class MultipleActionCheck extends Check {

    private boolean changedSneak = false, changedSprint = false;

    public MultipleActionCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * You cannot send multiple sprint changes or sneak changes within a tick.
         */

        switch (event.getPacketType()) {
            case CLIENT_TICK_END:
                changedSneak = changedSprint = false;
                break;
            case ENTITY_ACTION:
                WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);

                switch (wrapper.getAction()) {
                    case START_SNEAKING:
                    case STOP_SNEAKING:
                        if (changedSneak) fail();
                        changedSneak = true;
                        break;
                    case START_SPRINTING:
                    case STOP_SPRINTING:
                        if (changedSprint) fail();
                        changedSprint = true;
                        break;
                }
                break;
        }
    }
}
