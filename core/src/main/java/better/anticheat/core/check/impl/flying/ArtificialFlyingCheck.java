package better.anticheat.core.check.impl.flying;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;

/**
 * This check looks for excessive flying packets in a tick.
 */
@CheckInfo(
        name = "ArtificialFlying",
        category = "flying",
        requirements = {
                ClientFeatureRequirement.CLIENT_TICK_END
        }
)
public class ArtificialFlyingCheck extends Check {

    private boolean sentFlying = false, teleported = true;

    public ArtificialFlyingCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * The idea that the client can only send one position/rotation/flying packet per tick seems pretty basic at
         * first - until you look into the protocol a bit further. Our first issue is that teleporting players can
         * sometimes override this functionality, leading to multiple flying packets in a single tick. I don't 100% know
         * the specific details for what causes this as it can't always be recreated, but it's best to just exempt on
         * teleports. The second is due to a historical problem due to a Mojang desync patch.
         *
         * Minecraft has historically had a problem where blocks may sometimes not actually place because the server
         * believes you're excessively far from where you place - even when you aren't. This is related to 0.03 - an
         * issue that is better described in the ArtificialPositionCheck. Essentially, due to a quirk of Minecraft the
         * server did not know how far you actually are as the client would choose not to send its position.
         *
         * Seeing this as an issue, Mojang decided the best fix would be to send you position whenever you attempted to
         * place a place or use an item. This would allow you to send infinitely many positions in a tick, destroying
         * the idea that a flying packet was once a tick. I'm not sure when this was removed, but as of 1.21.4 it is no
         * longer in the game at least!
         */

        switch (event.getPacketType()) {
            case PLAYER_FLYING:
            case PLAYER_POSITION:
            case PLAYER_ROTATION:
            case PLAYER_POSITION_AND_ROTATION:
                if (player.getTeleportTracker().isTeleported()) break;
                if (sentFlying) fail();
                sentFlying = true;
                break;
            case CLIENT_TICK_END:
                sentFlying = teleported = false;
                break;
            case TELEPORT_CONFIRM:
                teleported = true;
                break;
        }
    }
}
