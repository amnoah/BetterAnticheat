package better.anticheat.core.util.ml;

import better.anticheat.core.configuration.ConfigSection;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

@Data
public class ModelConfig implements Serializable {
    /**
     * The display name of the model.
     */
    private final String displayName;

    /**
     * The type of the model.
     * <p>
     * Options: "decision_tree_gini", "decision_tree_entropy", "random_forest_gini", "random_forest_entropy", "logistic_regression", "fld", "knn", "lda".
     */
    private final String type;

    /**
     * The data slice to use (0 for yaw changes, 1 for offsets, 2 for combined).
     */
    private final int slice;

    /**
     * The names of the legit datasets to use.
     * <p>
     * Notice: this comment does not update when the plugin is updated, so check the wiki for the latest version.
     */
    private final List<String> legitDatasetNames;

    /**
     * The names of the cheat datasets to use.
     * <p>
     * Notice: this comment does not update when the plugin is updated, so check the wiki for the latest version.
     */
    private final List<String> cheatDatasetNames;

    /**
     * Should we extract statistics from the data before using the model?
     */
    private final boolean statistics;

    private final boolean shrink;

    /**
     * How many samples to use for runtime classification.
     */
    private final int samples;

    /**
     * Required average of samples to flag the player (9.5 == Definitely cheating, 3 == Probably not cheating).
     */
    private final double threshold;

    private final ConfigSection configSection;

    private @Nullable Function<double[][], Double> classifierFunction = null;
}
