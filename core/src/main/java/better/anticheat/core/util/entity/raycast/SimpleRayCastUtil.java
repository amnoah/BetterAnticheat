package better.anticheat.core.util.entity.raycast;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.player.tracker.impl.entity.type.EntityData;
import better.anticheat.core.util.entity.RayCastResult;
import better.anticheat.core.util.math.FastMathHelper;
import better.anticheat.core.util.type.entity.AxisAlignedBB;
import better.anticheat.core.util.type.entity.IAxisAlignedBoundingBox;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import lombok.RequiredArgsConstructor;

import java.util.Collection;

@RequiredArgsConstructor
public class SimpleRayCastUtil {
    private final BetterAnticheat betterAnticheat;

    public RayCastResult checkNormalPose(EntityData entityData, double[] yaws, double[] pitches, double[] poseHeights,
                                         Collection<com.github.retrooper.packetevents.protocol.world.Location> locations, final double expand, final double verticalExpand) {
        Vector3d result = null;
        boolean collided = false;
        double distance = 0;

        final var fastBB = this.betterAnticheat.isEntityTrackerFastEntityBox();


        // The base box to use, declared here to support fastBB easier
        //
        IAxisAlignedBoundingBox bruteForceBox = null;
        if (fastBB) {
            // We already expand and do everything in the case of fastBB. this reduces allocations and performance a lot.
            bruteForceBox = entityData.getCombinedBoundingBox();
        }

        for (com.github.retrooper.packetevents.protocol.world.Location location : locations) {
            final var locationBox = new AxisAlignedBB(location.getX(), location.getY(), location.getZ(), 0.6, 1.8);
            for (final var box : entityData.walk()) {
                for (final double eyeHeight : poseHeights) {
                    for (double bruteForceYaw : yaws) {
                        for (double bruteForcePitch : pitches) {
                            if (!fastBB) {
                                if (entityData.getType() == EntityTypes.PLAYER) {
                                    bruteForceBox = box.getBb().copy().expand(
                                            // 0.6 is the default player width in the bounding box
                                            expand + box.getPotentialOffsetAmountX() + ((entityData.getMaxPlayerWidth() - 0.6) / 2),
                                            // 1.8 is the default player height in the bounding box
                                            expand + verticalExpand + box.getPotentialOffsetAmountY() + (entityData.getMaxPlayerHeight() - 1.8),
                                            // 0.6 is the default player width in the bounding box
                                            expand + box.getPotentialOffsetAmountZ() + ((entityData.getMaxPlayerWidth() - 0.6) / 2)
                                    );
                                } else {
                                    bruteForceBox = box.getBb().copy().expand(
                                            expand + box.getPotentialOffsetAmountX(),
                                            expand + verticalExpand + box.getPotentialOffsetAmountY(),
                                            expand + box.getPotentialOffsetAmountZ()
                                    );
                                }
                            }

                            // 0.6 = attacker + target

                            if (bruteForceBox.intersectsWith(locationBox)) {
                                collided = true;
                            }

                            final double reach = 6;

                            final com.github.retrooper.packetevents.protocol.world.Location vec1 = getPositionEyes(location, eyeHeight);

                            final Vector3d vec31 = getVectorForRotation((float) Math.min(Math.max(bruteForcePitch, -90), 90),
                                    (float) (bruteForceYaw % 360));
                            final com.github.retrooper.packetevents.protocol.world.Location vec32 = addVector(vec1, vec31.getX() * reach, vec31.getY() * reach, vec31.getZ() * reach);

                            final Vector3d boxIntercept = bruteForceBox.calculateIntercept(vec1.getPosition(), vec32.getPosition());

                            if (boxIntercept != null) {
                                final double boxDist = boxIntercept.distance(vec1.getPosition());

                                if (result == null || boxDist < distance) {
                                    result = boxIntercept;
                                    distance = boxDist;
                                }
                            }
                        }
                    }
                }

                // No need to continue if using fastbb, as there is only one box.
                if (fastBB) {
                    break;
                }
            }
        }

        return new RayCastResult(result, distance, collided);
    }


    public com.github.retrooper.packetevents.protocol.world.Location addVector(final com.github.retrooper.packetevents.protocol.world.Location original, double x, double y, double z) {
        return new com.github.retrooper.packetevents.protocol.world.Location(addVector(original.getPosition(), x, y, z), original.getYaw(), original.getPitch());
    }

    public Vector3d addVector(final Vector3d original, double x, double y, double z) {
        return new Vector3d(original.getX() + x, original.getY() + y, original.getZ() + z);
    }

    public com.github.retrooper.packetevents.protocol.world.Location getPositionEyes(final com.github.retrooper.packetevents.protocol.world.Location location, final double eyeHeight) {
        return new com.github.retrooper.packetevents.protocol.world.Location(getPositionEyes(location.getPosition(), eyeHeight), location.getYaw(), location.getPitch());
    }

    public Vector3d getPositionEyes(final Vector3d location, final double eyeHeight) {
        return new Vector3d(location.getX(), location.getY() + eyeHeight, location.getZ());
    }

    public Vector3d getVectorForRotation(final float pitch, final float yaw) {
        final float f = FastMathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        final float f1 = FastMathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        final float f2 = -FastMathHelper.cos(-pitch * 0.017453292F);
        final float f3 = FastMathHelper.sin(-pitch * 0.017453292F);
        return new Vector3d(f1 * f2, f3, f * f2);
    }

}