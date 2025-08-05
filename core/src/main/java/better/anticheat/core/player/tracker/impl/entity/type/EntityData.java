package better.anticheat.core.player.tracker.impl.entity.type;

import better.anticheat.core.util.entity.ActivePlayerPose;
import better.anticheat.core.util.type.entity.IAxisAlignedBoundingBox;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import wtf.spare.sparej.fastlist.FastObjectArrayList;
import wtf.spare.sparej.incrementer.IntIncrementer;
import wtf.spare.sparej.xstate.bistate.DoubleBiState;
import wtf.spare.sparej.xstate.manystate.ObjectManyState;

import java.util.EnumMap;
import java.util.List;

@Data
public class EntityData implements Comparable<EntityData> {
    public final int id;
    private final EntityType type;
    private final EnumMap<EntityAttribute, Object> attributes = new EnumMap<>(EntityAttribute.class);
    private DoubleBiState serverPosX;
    private DoubleBiState serverPosY;
    private DoubleBiState serverPosZ;
    private ObjectManyState<EntityPose> poses = new ObjectManyState<>(100);
    private float height;
    private float width;
    private EntityTrackerState rootState;
    private IntIncrementer treeSize = new IntIncrementer(1);
    private IntIncrementer ticksSinceMove = new IntIncrementer(0);

    public int compareTo(@NotNull EntityData o) {
        return Integer.compare(o.getId(), this.id);
    }

    /*
     * Returns all entity states in this entity data tree
     */
    @NotNull
    public List<EntityTrackerState> walk() {
        return walk(rootState, new FastObjectArrayList<>(Math.max(0, treeSize.get())));
    }

    @NotNull
    public List<EntityTrackerState> walk(final EntityTrackerState parent, final List<EntityTrackerState> existingEntries) {
        for (final var child : parent.getChildren()) {
            walk(child, existingEntries);
        }
        existingEntries.add(parent);
        return existingEntries;
    }

    /**
     * Out of the current possible player poses, which is the max height?
     */
    public double getMaxPlayerHeight() {
        double maxHeight = 0;
        for (final var pose : poses) {
            maxHeight = Math.max(maxHeight, ActivePlayerPose.from(pose).getOverallHeight());
        }
        return maxHeight;
    }

    /**
     * Out of the current possible player poses, which is the max width?
     */
    public double getMaxPlayerWidth() {
        double maxWidth = 0;
        for (final var pose : poses) {
            maxWidth = Math.max(maxWidth, ActivePlayerPose.from(pose).getWidth());
        }
        return maxWidth;
    }

    /**
     * Gets the combined bounding box of all entities in the tree.
     */
    public IAxisAlignedBoundingBox getCombinedBoundingBox() {
        final var combinedBox = rootState.getBb().copy();
        walkCombinedBox(rootState, combinedBox);

        // Add the width/height pose offset if it is a player
        if (type == EntityTypes.PLAYER) {
            combinedBox.expand(
                    // 0.6 is the default player width in the bounding box
                    (getMaxPlayerWidth() - 0.6) / 2,
                    // Only expand the max height, not both heights
                    0,
                    // 0.6 is the default player width in the bounding box
                    (getMaxPlayerWidth() - 0.6) / 2
            );

            combinedBox.expandMax(
                    // We already expanded the width above
                    0,
                    // 1.8 is the default player height in the bounding box
                    (getMaxPlayerHeight() - 1.8),
                    // We already expanded the width above
                    0
            );
        }

        return combinedBox;
    }

    private void walkCombinedBox(final EntityTrackerState parent, final IAxisAlignedBoundingBox combinedBox) {
        for (final var child : parent.getChildren()) {
            walkCombinedBox(child, combinedBox);
        }
        combinedBox.setMinX(Math.min(combinedBox.getMinX(), parent.getBb().getMinX() - parent.getPotentialOffsetAmountX()));
        combinedBox.setMinY(Math.min(combinedBox.getMinY(), parent.getBb().getMinY() - parent.getPotentialOffsetAmountY()));
        combinedBox.setMinZ(Math.min(combinedBox.getMinZ(), parent.getBb().getMinZ() - parent.getPotentialOffsetAmountZ()));
        combinedBox.setMaxX(Math.max(combinedBox.getMaxX(), parent.getBb().getMaxX() + parent.getPotentialOffsetAmountX()));
        combinedBox.setMaxY(Math.max(combinedBox.getMaxY(), parent.getBb().getMaxY() + parent.getPotentialOffsetAmountY()));
        combinedBox.setMaxZ(Math.max(combinedBox.getMaxZ(), parent.getBb().getMaxZ() + parent.getPotentialOffsetAmountZ()));
    }

}
