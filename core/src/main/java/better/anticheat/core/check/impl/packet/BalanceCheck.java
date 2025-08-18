package better.anticheat.core.check.impl.packet;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;

/**
 * This check looks for game speed modifications and potentially artificial packets, like in Timer and Blink cheats.
 */
@CheckInfo(
        name = "Balance",
        category = "packet",
        requirements = {ClientFeatureRequirement.CLIENT_TICK_END}
)
public class BalanceCheck extends Check {

    private long lastTick = -1, balance = 0;
    private long maxBalance, minBalance;

    public BalanceCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * This check is built using a system commonly known in the anticheat community as "balance".
         * Essentially, a Minecraft client should run its tick at a minimum of every 50 milliseconds (it may take longer
         * if the game is set to a low framerate or lagging). This means anything under 50ms is typically indicative of
         * cheating. However, ticks may be received more frequently than 50ms apart if:
         * 1. The client recovers from a lag spike. The client may send many sudden "make up ticks" to get back where it
         * should be.
         * 2. The network freezes. Then, it will appear as if many packets appear at once despite them being regularly
         * spaced by the client.
         * Thus, the balance approach was born.
         *
         * The balance approach works like this:
         * 1. When a tick packet is received, it gives the player 50 balance (representing the 50ms gap).
         * 2. When a tick packet is received, it reduces the player's balance by the amount of ms elapsed since the
         * last tick. This should regularly result in balance returning to 0.
         * 3. There is a minimum threshold balance cannot go under to prevent abuse by repeatedly slowing and speeding
         * the game. This is, by default, at 3 seconds (-3000 balance).
         * 4. There is a maximum threshold balance cannot go over. This is how many milliseconds ahead the client should
         * be able to go at maximum. This is, by default, at 150 balance.
         *
         * This is an extremely basic version of the common balance check and could be improved with lag-spike
         * detections, but for now this will do!
         */

        switch (event.getPacketType()) {
            case PLAYER_LOADED:
                lastTick = 0;
                break;
            case CLIENT_TICK_END:
                if (lastTick == -1) return;

                long tick = System.currentTimeMillis();
                if (lastTick == 0) {
                    lastTick = tick;
                    return;
                }

                balance += 50;
                balance -= (tick - lastTick);
                balance = Math.max(balance, minBalance);

                if (balance > maxBalance) {
                    fail(balance);
                    balance = 0;
                }

                lastTick = tick;

                break;
        }
    }

    @Override
    public void load(ConfigSection section) {
        super.load(section);
        maxBalance = section.getOrSetIntegerWithComment("max-balance", 300, "The max MS a client can get ahead before it flags.");
        minBalance = section.getOrSetIntegerWithComment("min-balance", -3000, "The furthest a client can be accounted for lagging behind.");
    }
}
