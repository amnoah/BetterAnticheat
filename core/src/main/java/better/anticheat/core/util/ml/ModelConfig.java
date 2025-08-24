package better.anticheat.core.util.ml;

import better.anticheat.core.configuration.ConfigSection;
import lombok.Data;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

@Data
@ConfigSerializable
public class ModelConfig implements Serializable {

    private transient final String node;

    /**
     * The display name of the model.
     */
    @Setting("display-name")
    private final String displayName;

    /**
     * The type of the model.
     * <p>
     * Options: "decision_tree_gini", "decision_tree_entropy", "random_forest_gini", "random_forest_entropy".
     */
    @Setting("type")
    @Comment("Options: \"decision_tree_gini\", \"decision_tree_entropy\", \"random_forest_gini\", \"random_forest_entropy\".")
    private final String type;

    /**
     * The data slice to use (0 for yaw changes, 1 for offsets, 2 for combined).
     */
    @Setting("slice")
    @Comment("The data slice to use (0 for yaw changes, 1 for offsets, 2 for combined).")
    private final int slice;

    /**
     * The names of the legit datasets to use.
     * <p>
     * Notice: this comment does not update when the plugin is updated, so check the wiki for the latest version.
     */
    @Setting("legit-dataset-names")
    @Comment("Included: legit-small-2025-06-24-1 (notice: this comment does not update when the plugin is updated, so check the wiki for the latest version).")
    private final List<String> legitDatasetNames;

    /**
     * The names of the cheat datasets to use.
     * <p>
     * Notice: this comment does not update when the plugin is updated, so check the wiki for the latest version.
     */
    @Setting("cheat-dataset-names")
    @Comment("Included: cheat-small-2025-06-24-1 (notice: this comment does not update when the plugin is updated, so check the wiki for the latest version).")
    private final List<String> cheatDatasetNames;

    /**
     * Should we extract statistics from the data before using the model?
     */
    @Setting("statistics")
    @Comment("Should we extract statistics from the data before using the model?")
    private final boolean statistics;

    @Setting("shrink")
    @Comment("Should we shrink both datasets to the same size?")
    private final boolean shrink;

    /**
     * How many samples to use for runtime classification.
     */
    @Setting("samples")
    @Comment("How many samples to use for runtime classification.")
    private final int samples;

    /**
     * Required average of samples to flag the player (9.5 == Definitely cheating, 3 == Probably not cheating).
     */
    @Setting("alert-threshold")
    @Comment(
            "Required average of samples to flag the player (9.5 == Definitely cheating, 3 == Probably not cheating).\n" +
            "This is more a guide for the internal validation settings, than it is a strict value."
    )
    private final double alertThreshold;

    /**
     * Required average of samples to flag the player (9.5 == Definitely cheating, 3 == Probably not cheating).
     */
    @Setting("mitigation-threshold")
    @Comment(
            "Required average of samples to mitigate the player to remove any advantage from cheating.\n" +
            "This is more a guide for the internal validation settings, than it is a strict value."
    )
    private final double mitigationThreshold;

    @Setting("mitigation-only-ticks")
    @Comment("How many ticks to mitigate the player for, when we are flagging them, when only flagging them in mitigation state. Remember: 20 ticks per second.")
    private final int mitigationTicks;

    @Setting("tree-depth")
    @Comment(
            "For decision trees/random forests, what should be the maximum depth of the tree?\n" +
            "Higher values can increase accuracy, but lead to severe overfitting.\n" +
            "Lower values will significantly reduce overfitting, but slightly decrease accuracy (~15% drop from 40 to 25)."
    )
    private final int treeDepth;
    @Setting("node-depth")
    private final int nodeSize;

    private transient final ConfigSection configSection;

    private transient @Nullable Function<double[][], Double> classifierFunction = null;
}
