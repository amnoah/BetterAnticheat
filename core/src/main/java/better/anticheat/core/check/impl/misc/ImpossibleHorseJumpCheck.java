package better.anticheat.core.check.impl.misc;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

/**
 * This check looks for impossible vehicle jump boost modifications.
 */
@CheckInfo(name = "ImpossibleHorseJump", category = "misc")
public class ImpossibleHorseJumpCheck extends Check {

    public ImpossibleHorseJumpCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * The horse jump boost mechanic works in an interesting way:
         * First, it starts at 0. Every tick past that it adds 10 to it until it hits 100. After it hits 100, it
         * will decay by a value of 1 per tick until it hits 80, where it will stay. This means that every value
         * under 80 has to be divisible by 10 as it's only that low when rising.
         */

        if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) return;
        WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);

        if (wrapper.getAction() != WrapperPlayClientEntityAction.Action.START_JUMPING_WITH_HORSE) return;
        int boost = wrapper.getJumpBoost();

        if (boost < 80 && boost % 10 != 0) fail("<80");
        else if (boost > 100) fail(">100");
    }
}
