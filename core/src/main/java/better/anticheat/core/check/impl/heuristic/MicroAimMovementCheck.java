package better.anticheat.core.check.impl.heuristic;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.check.ClientFeatureRequirement;
import better.anticheat.core.player.Player;
import better.anticheat.core.util.EasyLoops;
import better.anticheat.core.util.MathUtil;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import wtf.spare.sparej.fastlist.evicting.ord.OrderedArrayFloatEvictingList;

/**
 * This check looks for very small mouse movement during combat.
 */
@CheckInfo(name = "MicroAimMovement", category = "heuristic", requirements = ClientFeatureRequirement.CLIENT_TICK_END)
public class MicroAimMovementCheck extends Check {

    private int ticksSinceAttack = 0, buffer = 0;
    private final OrderedArrayFloatEvictingList yawSamples = new OrderedArrayFloatEvictingList(128);
    private final OrderedArrayFloatEvictingList pitchSamples = new OrderedArrayFloatEvictingList(128);

    public MicroAimMovementCheck(BetterAnticheat plugin, Player player) {
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
        if (!player.getRotationTracker().isRotation()) return;

        if (ticksSinceAttack > 2) return;

        final float deltaPitch = Math.abs(player.getRotationTracker().getDeltaPitch());
        final float deltaYaw = Math.abs(player.getRotationTracker().getDeltaYaw());
        final boolean cinematic = player.getRotationTracker().isCinematic() || player.getRotationTracker().isCinematic2();

        if (cinematic) return;
        if (!(deltaPitch > 0 && deltaYaw > 0 && deltaPitch < 20 && deltaYaw < 20)) return;

        pitchSamples.push(deltaPitch);
        yawSamples.push(deltaYaw);

        if (!yawSamples.isFull()) return;

        final double averagePitch = MathUtil.getAverage(pitchSamples.getArray());
        final double averageYaw = MathUtil.getAverage(yawSamples.getArray());
        long count = 0;
        count += EasyLoops.count(pitchSamples, (delta -> delta < 0.001));
        count += EasyLoops.count(yawSamples, (delta -> delta < 0.001));

        if (((averageYaw > 0 && averageYaw < 1.1) || averagePitch <= 0.01) && count >= 80) {
            buffer++;
            if (buffer > 2) fail("pitch: " + averagePitch + ", yaw: " + averageYaw + ", count: " + count + ", bf: " + buffer);
        } else buffer = Math.max(--buffer, 0);
    }
}
