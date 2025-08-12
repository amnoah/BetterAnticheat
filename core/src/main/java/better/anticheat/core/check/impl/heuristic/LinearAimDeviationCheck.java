package better.anticheat.core.check.impl.heuristic;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import better.anticheat.core.util.MathUtil;
import better.anticheat.core.util.entity.EntityMath;
import better.anticheat.core.util.math.LinearRegression;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.doubles.DoubleDoublePair;
import wtf.spare.sparej.EvictingDeque;

/**
 * This check uses linear regression to catch aim cheats.
 */
@CheckInfo(name = "LinearAimDeviation", category = "heuristic")
public class LinearAimDeviationCheck extends Check {

    private final EvictingDeque<DoubleDoublePair> yaws = new EvictingDeque<>(25);
    private int ticksSinceAttack = 40, interactedEntity;

    private double aimBurstBuffer = 0, fastAimBuffer = 0, smoothFollowBuffer = 0;

    private Boolean tickEndSupported;

    public LinearAimDeviationCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public synchronized void handleReceivePlayPacket(PacketPlayReceiveEvent event) {
        // Track ticks since attack.
        switch (event.getPacketType()) {
            case INTERACT_ENTITY -> {
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    ticksSinceAttack = 0;
                    interactedEntity = wrapper.getEntityId();
                }
            }
            case CLIENT_TICK_END -> ticksSinceAttack = Math.min(++ticksSinceAttack, 1000);
        }

        /*
         * This check uses a method called linear regresison to catch aim cheats. Essentially, it assumes aim movement
         * is moving linearly and then generates a prediction as to what may come next if it is. Linear movement is
         * extremely hard to replicate genuinely, so we can use this to catch aim cheats that fail to humanize.
         * This catches tends to catch aim assists better than kill auras, interestingly.
         */

        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        // 1.21.1 fix
        if (tickEndSupported == null) {
            this.tickEndSupported = this.player.getUser().getClientVersion()
                    .isNewerThanOrEquals(ClientVersion.V_1_21_2);
        }
        if (!this.tickEndSupported) {
            ticksSinceAttack = Math.min(++ticksSinceAttack, 1000);
        }
        // Actual check
        if (!player.getRotationTracker().isRotation()) return;

        final float pitch = player.getRotationTracker().getPitch(), yaw = player.getRotationTracker().getYaw(), deltaYaw = player.getRotationTracker().getDeltaYaw();
        final Vector3d playerPos = new Vector3d(player.getPositionTracker().getX(), player.getPositionTracker().getY(), player.getPositionTracker().getZ());

        // Prevent falses by not including minimal or excessive aim changes.
        if (ticksSinceAttack > 20) return;
        if (deltaYaw < 0.05 || deltaYaw > 40) return;

        var entity = player.getEntityTracker().getEntities().get(interactedEntity);
        if (entity == null) return;

        // Ideally we should know exactly what position the entity is in... but we don't. So, for ease we'll use the latest.
        final Vector3d enemyPos = new Vector3d(entity.getServerPosX().getCurrent(), entity.getServerPosY().getCurrent(), entity.getServerPosZ().getCurrent());

        final double yawOffset = EntityMath.yawTo180F((float) EntityMath.getOffsetFromLocation(playerPos, enemyPos, yaw, pitch)[0]);
        yaws.add(new DoubleDoubleImmutablePair(deltaYaw, yawOffset));

        // Only go forward once we have enough rotations to properly derive statistics.
        if (!yaws.isFull()) return;

        // Establish all arrays that will be used in our computations.
        final double[] sampleOne = new double[yaws.size()];
        final double[] sampleTwo = new double[yaws.size()];
        final double[] differences = new double[yaws.size()];
        final double[] offsets = new double[yaws.size()];

        /*
         * Dump out data in our established arrays.
         * sampleOne - delta yaws
         * sampleTwo - yaw offsets in each tick
         * differences - the difference between delta yaw and yaw offset.
         */
        int i = 0;
        for (final var stored : yaws) {
            sampleOne[i] = stored.firstDouble();
            sampleTwo[i] = stored.secondDouble();
            differences[i] = Math.abs(stored.firstDouble() - stored.secondDouble());
            i++;
        }

        final LinearRegression regression = LinearRegression.simple(sampleOne, sampleTwo);

        // Run our linear regression predictions.
        i = 0;
        for (final var stored : yaws) {
            final double regObj = regression.predict(stored.firstDouble());
            final double out = Math.abs(regObj - stored.secondDouble());
            offsets[i] = out;
            i++;
        }

        /*
         * Here we build all the various statistics for usage in our individual checks. This is all pretty standard, and
         * unfortunately what we do with these statistics doesn't have much of an explanation as many are built via
         * trial and error.
         */
        final double avg = MathUtil.getAverage(offsets);
        final double stddev = MathUtil.getStandardDeviation(offsets);

        final double interceptStdErr = regression.interceptStdErr();
        final double slopeStdErr = regression.slopeStdErr();
        final double intercept = regression.getIntercept();
        final double slope = regression.getSlope();
        final double slopeABS = Math.abs(slope);

        final double avgDiff = MathUtil.getAverage(differences);
        final double diffDev = MathUtil.getStandardDeviation(differences);

        final double avgRot = MathUtil.getAverage(sampleTwo);
        boolean anyFlag = false;

        // These values should detect partial aim assists. These are aim assists that push the player's aim toward their
        // target but don't actually lock on.
        aim_burst:
        {
            if (avg > 1.2 && avg < 3.5 && interceptStdErr < 0.75 && slopeStdErr < 0.3 && intercept > 3 && intercept < 10 && avgDiff > 3 &&
                    avgDiff < 10 && diffDev > 7.5 && diffDev < 30 && avgRot > 1 && avgRot < 4.0 && ticksSinceAttack < (20 * 20) &&
                    (slopeABS > 0.27 || slopeABS < 0.05)) {
                anyFlag = true;
                aimBurstBuffer++;
                if (aimBurstBuffer > 2) fail("Aim Burst");
            }
        }

        // These values should catch bad randomization cheats.
        bad_randomization:
        {
            if (stddev < 2.0 && interceptStdErr < 10 && slopeStdErr < 10 && avgRot > 2.2) {
                anyFlag = true;
                fail("Bad Randomization");
            }
        }

        // These values look for fast aiming that is accurate, non-jittery, but still human-like.
        fast_aim:
        {
            if (avg < 2 && interceptStdErr < 15 && slopeStdErr < 1 && slopeABS > 0.1 && slopeABS < 1 && intercept < 20 && intercept > 0 && slope > -0.1 && avgRot > 8) {
                anyFlag = true;
                fastAimBuffer += 1;
                if (fastAimBuffer >= 5) fail("Fast Aim");
            }
        }

        // These are magic values meant to catch LiquidBounce.
        liquid_bounce:
        {
            var flags = 0.0;

            if (interceptStdErr < 0.8) flags += 0.5;
            if (slopeStdErr < 0.3) flags++;
            if (slope < -0.2) flags += 0.5;
            if (slope < -3) flags++;
            if (Math.abs(slope) < 0.1) flags += 0.5;
            if (slope < -0.02 && slope > -0.1) flags++;
            if (intercept < 12 && interceptStdErr < 1.2) flags += 0.5;
            if (intercept < 15) flags += 0.25;
            if (avgRot > 10) flags += 0.25;
            if (avgDiff <= 11) flags += 0.25;

            if (flags >= 3 && !player.getTeleportTracker().isTeleported()) {
                anyFlag = true;
                fail("LB");
            }
        }

        // These values should catch limited randomization/robotic movement.
        low_randomization:
        {
            // Run the following if within 30 seconds of an attack.
            if (ticksSinceAttack > (30 * 20)) break low_randomization;

            if (avg < 0.5 && stddev < 1.75 && avgDiff > 1.5 && diffDev > 3 && !(avgDiff < 10 && avg < 0.3 && stddev < 0.8 && diffDev < 6)) {// TODO: Sensitivity! && data.getRotationProcessor().getSensitivityY() < 60)) {
                if (avg < 0.45 && (player.getRotationTracker().isCinematic() || player.getRotationTracker().isCinematic2() || avg < 0.1)) {
                    anyFlag = true;
                    fail("Low Randomization");
                }
            }
        }

        // These values should detect smoothly following a moving entity.
        smooth_follow:
        {
            // Only look if the player is actively following a moving entity.
            if (entity.getTicksSinceMove().get() > 20 * 20) break smooth_follow;

            if (interceptStdErr < 2 && slopeStdErr < 2 && stddev < 3 && avg < 4 && avgDiff < 3 && avgRot > 2) {
                anyFlag = true;
                smoothFollowBuffer += 1;
                if (smoothFollowBuffer < 10) break smooth_follow;
                fail("SmoothFollow");
            } else smoothFollowBuffer -= 0.4;
        }

        if (!anyFlag) {
            fastAimBuffer = Math.max(fastAimBuffer - 0.1, 0);
            if (ticksSinceAttack < (20 * 20)) {
                aimBurstBuffer = Math.max(aimBurstBuffer - 0.035, 0);
            }
        }
    }
}
