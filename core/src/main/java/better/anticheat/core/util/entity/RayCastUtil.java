package better.anticheat.core.util.entity;

import better.anticheat.core.player.tracker.impl.entity.type.EntityData;
import better.anticheat.core.util.math.FastMathHelper;
import better.anticheat.core.util.type.entity.AxisAlignedBB;
import better.anticheat.core.util.type.entity.IAxisAlignedBoundingBox;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import lombok.experimental.UtilityClass;

import java.util.Collection;

@UtilityClass
public class RayCastUtil {
    public RayCastResult checkNormalPose(EntityData entityData, double[] yaws, double[] pitches,
                                         Collection<com.github.retrooper.packetevents.protocol.world.Location> locations, final double expand, final double verticalExpand) {
        Vector3d result = null;
        boolean collided = false;
        double distance = 0;

        for (com.github.retrooper.packetevents.protocol.world.Location location : locations) {
            final var locationBox = new AxisAlignedBB(location.getX(), location.getY(), location.getZ(), 0.6, 1.8);
            for (final var box : entityData.walk()) {
                for (double bruteForceYaw : yaws) {
                    for (double bruteForcePitch : pitches) {
                        IAxisAlignedBoundingBox bruteForceBox;
                        if (entityData.getType() == EntityTypes.PLAYER) {
                            bruteForceBox = box.getBb().copy().expand(
                                    expand + box.getPotentialOffsetAmountX(),
                                    expand + verticalExpand + box.getPotentialOffsetAmountY() + (entityData.getMaxPlayerHeight() - 1.8),
                                    expand + box.getPotentialOffsetAmountZ()
                            );
                        } else {
                            bruteForceBox = box.getBb().copy().expand(
                                    expand + box.getPotentialOffsetAmountX(),
                                    expand + verticalExpand + box.getPotentialOffsetAmountY(),
                                    expand + box.getPotentialOffsetAmountZ()
                            );
                        }

                        // 0.6 = attacker + target

                        if (bruteForceBox.intersectsWith(locationBox)) {
                            collided = true;
                        }

                        final double reach = 6;

                        final com.github.retrooper.packetevents.protocol.world.Location vec1 = getPositionEyes(location);

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
        }

        return new RayCastResult(result, distance, collided);
    }


    public com.github.retrooper.packetevents.protocol.world.Location addVector(final com.github.retrooper.packetevents.protocol.world.Location original, double x, double y, double z) {
        return new com.github.retrooper.packetevents.protocol.world.Location(addVector(original.getPosition(), x, y, z), original.getYaw(), original.getPitch());
    }

    public Vector3d addVector(final Vector3d original, double x, double y, double z) {
        return new Vector3d(original.getX() + x, original.getY() + y, original.getZ() + z);
    }

    public com.github.retrooper.packetevents.protocol.world.Location getPositionEyes(final com.github.retrooper.packetevents.protocol.world.Location location) {
        return new com.github.retrooper.packetevents.protocol.world.Location(getPositionEyes(location.getPosition()), location.getYaw(), location.getPitch());
    }

    public Vector3d getPositionEyes(final Vector3d location) {
        float eyeHeight = 1.62F;
        return new Vector3d(location.getX(), location.getY() + (double) eyeHeight, location.getZ());
    }

    public Vector3d getVectorForRotation(final float pitch, final float yaw) {
        final float f = FastMathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        final float f1 = FastMathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        final float f2 = -FastMathHelper.cos(-pitch * 0.017453292F);
        final float f3 = FastMathHelper.sin(-pitch * 0.017453292F);
        return new Vector3d(f1 * f2, f3, f * f2);
    }
}