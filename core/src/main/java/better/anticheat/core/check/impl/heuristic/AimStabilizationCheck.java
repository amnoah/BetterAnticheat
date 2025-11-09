package better.anticheat.core.check.impl.heuristic;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

/**
 * This check looks for artificially smoothed/stabilized aim.
 */
@CheckInfo(name = "AimStabilization", category = "heuristic")
public class AimStabilizationCheck extends Check {

    private int ticksSinceAttack = 0;
    private double buffer = 0;
    private Boolean supportsTickEnd;

    public AimStabilizationCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {
        switch (event.getPacketType()) {
            case INTERACT_ENTITY -> {
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
                ticksSinceAttack = 0;
            }
            case CLIENT_TICK_END -> ticksSinceAttack++;
        }

        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;

        // 1.21.1 fix
        if (supportsTickEnd == null) {
            this.supportsTickEnd = this.player.getUser().getClientVersion()
                    .isNewerThanOrEquals(ClientVersion.V_1_21_2);
        }
        if (!this.supportsTickEnd) {
            ticksSinceAttack = Math.min(++ticksSinceAttack, 1000);
        }

        if (!player.getRotationTracker().isRotation()) return;

        // Only run if the player has been in combat.
        if (ticksSinceAttack > 10) return;

        final float lastDeltaPitch = Math.abs(player.getRotationTracker().getLastDeltaPitch());
        final float deltaPitch = Math.abs(player.getRotationTracker().getDeltaPitch());
        final float deltaYaw = Math.abs(player.getRotationTracker().getDeltaYaw());
        final double pitchAcceleration = Math.abs(deltaPitch - lastDeltaPitch);

        // Magic aim movement values!
        if (!(deltaPitch > 3.d && lastDeltaPitch < 5.d && deltaYaw > 0.5)) return;

        // Magic value! Don't question.
        final double PITest = Math.abs(Math.PI / pitchAcceleration);
        if (PITest < 8D) {
            buffer = Math.max(buffer - 0.25, 0);
            return;
        }

        buffer += 1;
        if (buffer < 10) return;

        fail("rat: " + PITest);
    }
}
