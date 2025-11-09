package better.anticheat.core.check.impl.flying;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * This check looks for missing position packets.
 */
@CheckInfo(name = "FlyingSequence", category = "flying", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
@Slf4j
public class FlyingSequenceCheck extends Check {

    private int ticks = -1;

    public FlyingSequenceCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {

        /*
         * One technique Mojang has implement to prevent position desyncs was adding a position packet that sends once
         * a second regardless of whether a player moves or not. Given that the client ticks 20 times in a second, we
         * can then check if the client passes 20 ticks without sending a position packet to see if it may be trying to
         * hide its position (maybe a badly coded blink cheat) or trying to just avoid sending related packets.
         */

        switch (event.getPacketType()) {
            case PLAYER_LOADED:
                ticks = 0;
                break;
            case VEHICLE_MOVE:
            case PLAYER_POSITION:
            case PLAYER_POSITION_AND_ROTATION:
                if (ticks < 0) return;
                ticks = 0;
                break;
            case CLIENT_TICK_END:
                // Prevent until first position is sent.
                if (ticks < 0) return;

                // Fix dead player false flags
                if (player.getPlayerStatusTracker().getIsDead().anyTrue()) {
                    ticks = 0;
                    log.debug("[BetterAntiCheat] Player is dead, resetting ticks: {}", player.getPlayerStatusTracker().getIsDead());
                    return;
                }

                if (++ticks > 20) fail(ticks);
                break;
        }
    }
}
