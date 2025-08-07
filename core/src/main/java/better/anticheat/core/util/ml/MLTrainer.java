package better.anticheat.core.util.ml;

import better.anticheat.core.util.MathUtil;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import smile.base.cart.SplitRule;
import smile.classification.*;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

import java.io.IOException;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Getter
public class MLTrainer {
    private final double[][] legitData;
    private final double[][] cheatingData;

    private double[][] legitTrain;
    private double[][] cheatingTrain;

    private double[][] train;
    private final int[] labels;

    private final int slice;


    private final boolean statistics;

    private DecisionTree giniTree;
    private DecisionTree entropyTree;
    private RandomForest giniForest;
    private RandomForest entropyForest;

    public static final StructType PREDICTION_STRUCT = new StructType(
            new StructField("V2", DataTypes.IntType),
            new StructField("V3", DataTypes.IntType),
            new StructField("V4", DataTypes.IntType),
            new StructField("V5", DataTypes.IntType),
            new StructField("V6", DataTypes.IntType),
            new StructField("V7", DataTypes.IntType),
            new StructField("V8", DataTypes.IntType),
            new StructField("V9", DataTypes.IntType),
            new StructField("V10", DataTypes.IntType),
            new StructField("V11", DataTypes.IntType)
    );

    public static final StructType PREDICTION_STRUCT_XL = new StructType(
            new StructField("V2", DataTypes.IntType),
            new StructField("V3", DataTypes.IntType),
            new StructField("V4", DataTypes.IntType),
            new StructField("V5", DataTypes.IntType),
            new StructField("V6", DataTypes.IntType),
            new StructField("V7", DataTypes.IntType),
            new StructField("V8", DataTypes.IntType),
            new StructField("V9", DataTypes.IntType),
            new StructField("V10", DataTypes.IntType),
            new StructField("V11", DataTypes.IntType),
            new StructField("V12", DataTypes.IntType),
            new StructField("V13", DataTypes.IntType),
            new StructField("V14", DataTypes.IntType),
            new StructField("V15", DataTypes.IntType),
            new StructField("V16", DataTypes.IntType),
            new StructField("V17", DataTypes.IntType),
            new StructField("V18", DataTypes.IntType),
            new StructField("V19", DataTypes.IntType),
            new StructField("V20", DataTypes.IntType),
            new StructField("V21", DataTypes.IntType),
            new StructField("V22", DataTypes.IntType),
            new StructField("V23", DataTypes.IntType),
            new StructField("V24", DataTypes.IntType),
            new StructField("V25", DataTypes.IntType),
            new StructField("V26", DataTypes.IntType),
            new StructField("V27", DataTypes.IntType),
            new StructField("V28", DataTypes.IntType)
    );



    /**
     * @param legitData         the non cheating training data
     * @param cheatingData      the cheating training data
     * @param slice             the slice of the data to use. Options: 0, 1, or 2.
     * @param shrink            should we shrink the data to the smallest of the two?
     * @param statistics        should we generate statistics for the data, instead of using raw data.
     * @param trainRandomForest should we train a random forest, resulting in many unwanted logs.
     */
    public MLTrainer(final double[][][] legitData, final double[][][] cheatingData, final int slice, final boolean shrink, final boolean statistics, boolean trainRandomForest) {
        this(legitData, cheatingData, slice, shrink, statistics, trainRandomForest, 40, 40, 4, 4, 30, 30, 4, 4);
    }

    /**
     * @param legitData             the non cheating training data
     * @param cheatingData          the cheating training data
     * @param slice                 the slice of the data to use. Options: 0, 1, or 2.
     * @param shrink                should we shrink the data to the smallest of the two?
     * @param statistics            should we generate statistics for the data, instead of using raw data.
     * @param trainRandomForest     should we train a random forest, resulting in many unwanted logs.
     * @param giniMaxDepth          the maximum depth for the gini tree
     * @param entropyMaxDepth       the maximum depth for the entropy tree
     * @param giniForestMaxDepth    the maximum depth for the gini forest
     * @param entropyForestMaxDepth the maximum depth for the entropy forest
     */
    public MLTrainer(final double[][][] legitData, final double[][][] cheatingData, final int slice, final boolean shrink, final boolean statistics, boolean trainRandomForest, int giniMaxDepth, int entropyMaxDepth, int giniForestMaxDepth, int entropyForestMaxDepth) {
        this(legitData, cheatingData, slice, shrink, statistics, trainRandomForest, giniMaxDepth, entropyMaxDepth, 4, 4, 30, 30, giniForestMaxDepth, entropyForestMaxDepth);
    }

    /**
     * @param legitData             the non cheating training data
     * @param cheatingData          the cheating training data
     * @param slice                 the slice of the data to use. Options: 0, 1, or 2.
     * @param shrink                should we shrink the data to the smallest of the two?
     * @param statistics            should we generate statistics for the data, instead of using raw data.
     * @param trainRandomForest     should we train a random forest, resulting in many unwanted logs.
     * @param giniMaxDepth          max depth for gini decision tree
     * @param entropyMaxDepth       max depth for entropy decision tree
     * @param giniNodeSize          min node size for gini decision tree
     * @param entropyNodeSize       min node size for entropy decision tree
     * @param giniForestMaxDepth    max depth for gini random forest
     * @param entropyForestMaxDepth max depth for entropy random forest
     * @param giniForestNodeSize    min node size for gini random forest
     * @param entropyForestNodeSize min node size for entropy random forest
     */
    public MLTrainer(final double[][][] legitData, final double[][][] cheatingData, final int slice, final boolean shrink, final boolean statistics, boolean trainRandomForest,
                     int giniMaxDepth, int entropyMaxDepth, int giniNodeSize, int entropyNodeSize,
                     int giniForestMaxDepth, int entropyForestMaxDepth, int giniForestNodeSize, int entropyForestNodeSize) {
        this.legitData = legitData[slice];
        this.cheatingData = cheatingData[slice];

        this.statistics = statistics;
        this.slice = slice;

        if (shrink) {
            this.legitTrain = Arrays.copyOf(this.legitData, Math.min(this.legitData.length, this.cheatingData.length));
            this.cheatingTrain = Arrays.copyOf(this.cheatingData, Math.min(this.legitData.length, this.cheatingData.length));
        } else {
            this.legitTrain = this.legitData;
            this.cheatingTrain = this.cheatingData;
        }

        this.train = new double[legitTrain.length + cheatingTrain.length][];
        System.arraycopy(legitTrain, 0, this.train, 0, legitTrain.length);
        System.arraycopy(cheatingTrain, 0, this.train, legitTrain.length, cheatingTrain.length);

        this.labels = new int[this.legitTrain.length + this.cheatingTrain.length];
        for (var i = 0; i < this.legitTrain.length; i++) {
            this.labels[i] = 0;
        }
        for (var i = 0; i < this.cheatingTrain.length; i++) {
            this.labels[i + this.legitTrain.length] = 10;
        }

        this.buildDTree(trainRandomForest, giniMaxDepth, entropyMaxDepth, giniNodeSize, entropyNodeSize,
                giniForestMaxDepth, entropyForestMaxDepth, giniForestNodeSize, entropyForestNodeSize);
    }

    public double[] statistiizeAndRetainFive(final double[] datum) {
        final var shrunk = new double[5];
        System.arraycopy(datum, 0, shrunk, 0, 5);

        // Compute last 3 accelerations before the attack from the first 5 samples:
        // velocity diffs (size 4)
        final double[] vel = new double[4];
        for (int i = 0; i < 4; i++) {
            vel[i] = shrunk[i + 1] - shrunk[i];
        }
        // acceleration diffs (size 3)
        final double[] acc3 = new double[3];
        for (int i = 0; i < 3; i++) {
            acc3[i] = vel[i + 1] - vel[i];
        }

        // Stats on the 3-acceleration window
        final double accAutocorr1 = MathUtil.autocorr(acc3, 1);
        final double accSkew = MathUtil.getSkewness(acc3);
        final double accStd = MathUtil.getStandardDeviation(acc3);
        final int accZeroX = MathUtil.zeroCrossings(acc3);
        final double accAvg = MathUtil.getAverage(acc3);

        // Additional yaw-related features:
        // From last 5 yaw deltas (shrunk)
        final double deltaYawKurtosis = MathUtil.getKurtosis(shrunk);
        final double deltaYawEntropy  = MathUtil.getEntropy(shrunk);
        // From entire yaw deltas (all data)
        final double deltaYawKurtosisAll = MathUtil.getKurtosis(datum);
        // From last 3 yaw accelerations (acc3)
        final double accelerationYawKurtosis = MathUtil.getKurtosis(acc3);
        final double accelerationYawEntropy  = MathUtil.getEntropy(acc3);

        return new double[]{
                MathUtil.getStandardDeviation(shrunk), // 0
                MathUtil.getSkewness(shrunk),          // 1
                MathUtil.getAverage(shrunk),           // 2
                MathUtil.getFluctuation(shrunk),       // 3
                MathUtil.getOscillation(shrunk),       // 4
                MathUtil.getEnergy(shrunk),            // 5
                MathUtil.getEnergy(datum),             // 6
                MathUtil.autocorr(datum, 1),           // 7
                MathUtil.autocorr(datum, 5),           // 8
                shrunk[0],                              // 9
                shrunk[1],                              // 10
                shrunk[2],                              // 11
                shrunk[3],                              // 12
                shrunk[4],                              // 13
                accAutocorr1,                           // 14
                accSkew,                                // 15
                accStd,                                 // 16
                accZeroX,                               // 17
                accAvg,                                 // 18
                acc3[0],                                // 19
                acc3[1],                                // 20
                acc3[2],                                // 21
                deltaYawKurtosis,                       // 22
                deltaYawEntropy,                        // 23
                accelerationYawKurtosis,                // 24
                accelerationYawEntropy,                 // 25
                deltaYawKurtosisAll                     // 26
        };
    }

    public int[] prepareInputForTree(final double[][] input) {
        double[] prepared = statistics ? this.statistiizeAndRetainFive(input[slice]) : input[slice];
        int[] tupleData = new int[statistics ? prepared.length : 10];

        for (int i = 0; i < prepared.length; i++) {
            tupleData[i] = (int) Math.round(prepared[i] * 2_500_000);
        }

        return tupleData;
    }

    private void buildDTree(final boolean trainRandomForest, int giniMaxDepth, int entropyMaxDepth, int giniNodeSize, int entropyNodeSize,
                            int giniForestMaxDepth, int entropyForestMaxDepth, int giniForestNodeSize, int entropyForestNodeSize) {
        final double[][] statTrain = new double[this.train.length][];
        if (this.statistics) {
            for (int i = 0; i < this.train.length; i++) {
                statTrain[i] = statistiizeAndRetainFive(this.train[i]);
            }
        } else {
            System.arraycopy(this.train, 0, statTrain, 0, this.train.length);
        }

        var xArrays = new int[statTrain.length][];
        if (this.statistics) {
            for (int i = 0; i < statTrain.length; i++) {
                final var array = statTrain[i];
                xArrays[i] = new int[1 + array.length];
                xArrays[i][0] = this.labels[i];
                for (int j = 0; j < array.length; j++) {
                    xArrays[i][j + 1] = (int) Math.round(array[j] * 2_500_000);
                }
            }
        } else {
            for (int i = 0; i < statTrain.length; i++) {
                final var array = statTrain[i];
                if (array.length > 5) {
                    xArrays[i] = new int[]{
                            this.labels[i],
                            (int) Math.round(array[0] * 2_500_000),
                            (int) Math.round(array[1] * 2_500_000),
                            (int) Math.round(array[2] * 2_500_000),
                            (int) Math.round(array[3] * 2_500_000),
                            (int) Math.round(array[4] * 2_500_000),
                            (int) Math.round(array[5] * 2_500_000),
                            (int) Math.round(array[6] * 2_500_000),
                            (int) Math.round(array[7] * 2_500_000),
                            (int) Math.round(array[8] * 2_500_000),
                            (int) Math.round(array[9] * 2_500_000)
                    };
                } else {
                    xArrays[i] = new int[]{
                            this.labels[i],
                            (int) Math.round(array[0] * 2_500_000),
                            (int) Math.round(array[1] * 2_500_000),
                            (int) Math.round(array[2] * 2_500_000),
                            (int) Math.round(array[3] * 2_500_000),
                            (int) Math.round(array[4] * 2_500_000),
                            0,
                            0,
                            0,
                            0,
                            0
                    };
                }
            }
        }
        var xArrayListForm = Arrays.asList(xArrays);
        Collections.shuffle(xArrayListForm);
        xArrays = xArrayListForm.toArray(new int[0][]);
        final var df = DataFrame.of(xArrays);
        log.debug("DataFrame: {}", df.toString(0, 20, true));
        this.giniTree = DecisionTree.fit(Formula.lhs("V1"), df, new DecisionTree.Options(SplitRule.GINI, giniMaxDepth, 0, giniNodeSize));
        this.entropyTree = DecisionTree.fit(Formula.lhs("V1"), df, new DecisionTree.Options(SplitRule.ENTROPY, entropyMaxDepth, 0, entropyNodeSize));

        // Shitty Forests
        if (!trainRandomForest) return;
        this.giniForest = RandomForest.fit(Formula.lhs("V1"), df, new RandomForest.Options(125, 0, SplitRule.GINI, giniForestMaxDepth, 0, giniForestNodeSize, 1.0, null, null, null));
        this.entropyForest = RandomForest.fit(Formula.lhs("V1"), df, new RandomForest.Options(125, 0, SplitRule.ENTROPY, entropyForestMaxDepth, 0, entropyForestNodeSize, 1.0, null, null, null));
    }

    /**
     * Creates a prediction function from configured datasets and model parameters.
     *
     * @param legitDatasetNames    List of filenames for legitimate data recordings.
     * @param cheatingDatasetNames List of filenames for cheating data recordings.
     * @param modelType            The type of model to train. Options: "decision_tree_gini", "decision_tree_entropy", "random_forest_gini", "random_forest_entropy", "logistic_regression", "fld", "knn", "lda".
     * @param slice                The data slice to use (0 for yaws, 1 for offsets, 2 for enhancedOffsets).
     * @param intlify              Whether to intlify the data.
     * @param statistics           Whether to generate statistics from the data.
     * @param shrink
     * @param maxDepth
     * @param dataDirectory        The directory where recording data is stored.
     * @return A function that takes raw input data and returns a prediction score.
     * @throws IOException if there is an error reading the data files.
     */
    public static Function<double[][], Double> create(
            List<String> legitDatasetNames,
            List<String> cheatingDatasetNames,
            String modelType,
            int slice,
            boolean intlify,
            boolean statistics,
            boolean shrink,
            int maxDepth,
            int nodeSize,
            Path dataDirectory
    ) throws IOException {
        List<double[][][]> legitDataList = new ArrayList<>();
        for (String name : legitDatasetNames) {
            try {
                double[][][] data = RecordingUtil.loadData(name, dataDirectory);
                if (data != null) {
                    legitDataList.add(data);
                } else {
                    log.error("[BetterAnticheat] Failed to load ML data from file {} (legit split), please check your ML configuration.", name);
                }
            } catch (IOException e) {
                log.error("Failed to load legitimate dataset: {}", name, e);
            }
        }

        List<double[][][]> cheatingDataList = new ArrayList<>();
        for (String name : cheatingDatasetNames) {
            try {
                double[][][] data = RecordingUtil.loadData(name, dataDirectory);
                if (data != null) {
                    cheatingDataList.add(data);
                } else {
                    log.error("[BetterAnticheat] Failed to load ML data from file {} (cheating split), please check your ML configuration.", name);
                }
            } catch (IOException e) {
                log.error("Failed to load cheating dataset: {}", name, e);
            }
        }

        double[][][] mergedLegitData = mergeData(legitDataList);
        double[][][] mergedCheatingData = mergeData(cheatingDataList);

        final var trainer = new MLTrainer(mergedLegitData, mergedCheatingData, slice, shrink, statistics, modelType.toLowerCase().contains("forest"), maxDepth, maxDepth, nodeSize, nodeSize, maxDepth, maxDepth, nodeSize, nodeSize);

        return switch (modelType.toLowerCase()) {
            case "decision_tree_gini" -> {
                final var model = trainer.getGiniTree();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    int[] tupleData = trainer.prepareInputForTree(input);
                    return (double) model.predict(Tuple.of(statistics ? PREDICTION_STRUCT_XL : PREDICTION_STRUCT, tupleData));
                };
            }
            case "decision_tree_entropy" -> {
                final var model = trainer.getEntropyTree();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    int[] tupleData = trainer.prepareInputForTree(input);
                    return (double) model.predict(Tuple.of(statistics ? PREDICTION_STRUCT_XL : PREDICTION_STRUCT, tupleData));
                };
            }
            case "random_forest_gini" -> {
                final var model = trainer.getGiniForest();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    int[] tupleData = trainer.prepareInputForTree(input);
                    return (double) model.predict(Tuple.of(statistics ? PREDICTION_STRUCT_XL : PREDICTION_STRUCT, tupleData));
                };
            }
            case "random_forest_entropy" -> {
                final var model = trainer.getEntropyForest();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    int[] tupleData = trainer.prepareInputForTree(input);
                    return (double) model.predict(Tuple.of(statistics ? PREDICTION_STRUCT_XL : PREDICTION_STRUCT, tupleData));
                };
            }

            default -> throw new IllegalArgumentException("Unknown model type: " + modelType);
        };
    }

    private static double[][][] mergeData(List<double[][][]> dataList) {
        int totalYaws = 0;
        int totalOffsets = 0;
        int totalEnhancedOffsets = 0;

        for (double[][][] data : dataList) {
            totalYaws += data[0].length;
            totalOffsets += data[1].length;
            totalEnhancedOffsets += data[2].length;
        }

        double[][] combinedYaws = new double[totalYaws][];
        double[][] combinedOffsets = new double[totalOffsets][];
        double[][] combinedEnhancedOffsets = new double[totalEnhancedOffsets][];

        int yawsIndex = 0;
        int offsetsIndex = 0;
        int enhancedOffsetsIndex = 0;

        for (double[][][] data : dataList) {
            System.arraycopy(data[0], 0, combinedYaws, yawsIndex, data[0].length);
            yawsIndex += data[0].length;
            System.arraycopy(data[1], 0, combinedOffsets, offsetsIndex, data[1].length);
            offsetsIndex += data[1].length;
            System.arraycopy(data[2], 0, combinedEnhancedOffsets, enhancedOffsetsIndex, data[2].length);
            enhancedOffsetsIndex += data[2].length;
        }

        return new double[][][]{combinedYaws, combinedOffsets, combinedEnhancedOffsets};
    }
}
