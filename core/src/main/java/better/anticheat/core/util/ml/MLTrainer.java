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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.github.luben.zstd.Zstd;

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

    private final boolean intlify;
    private final boolean statistics;

    private DecisionTree giniTree;
    private DecisionTree entropyTree;
    private RandomForest giniForest;
    private RandomForest entropyForest;

    private static final StructType PREDICTION_STRUCT = new StructType(
            new StructField("V2", DataTypes.IntType),
            new StructField("V3", DataTypes.IntType),
            new StructField("V4", DataTypes.IntType),
            new StructField("V5", DataTypes.IntType),
            new StructField("V6", DataTypes.IntType)
    );

    /**
     * @param legitData         the non cheating training data
     * @param cheatingData      the cheating training data
     * @param slice             the slice of the data to use. Options: 0, 1, or 2.
     * @param shrink            should we shrink the data to the smallest of the two?
     * @param intlify           should we do some changes improve compatibility of weirdly shaped doubles. NOTE: This trims the decimal places to the first 7 only, among other tasks.
     * @param statistics        should we generate statistics for the data, instead of using raw data.
     * @param trainRandomForest should we train a random forest, resulting in many unwanted logs.
     */
    public MLTrainer(final double[][][] legitData, final double[][][] cheatingData, final int slice, final boolean shrink, final boolean intlify, final boolean statistics, boolean trainRandomForest) {
        this.legitData = legitData[slice];
        this.cheatingData = cheatingData[slice];

        this.intlify = intlify;
        this.statistics = statistics;
        this.slice = slice;

        if (this.statistics) {
            for (int i = 0; i < this.legitData.length; i++) {
                this.legitData[i] = statistiize(this.legitData[i]);
            }

            for (int i = 0; i < this.cheatingData.length; i++) {
                this.cheatingData[i] = statistiize(this.cheatingData[i]);
            }
        }

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

        this.buildDTree(trainRandomForest);

        // Intlify ONLY after building the tree
        if (this.intlify) {
            this.legitTrain = this.intlifyTwoDeep(this.legitTrain);
            this.cheatingTrain = this.intlifyTwoDeep(this.cheatingTrain);

            this.legitTrain = this.intlifyTwoDeep(this.legitTrain);
            this.cheatingTrain = this.intlifyTwoDeep(this.cheatingTrain);

            this.train = this.intlifyTwoDeep(this.train);
        }
    }

    public double[] statistiize(final double[] datum) {
        return new double[]{
                MathUtil.getStandardDeviation(datum),
                MathUtil.getSkewness(datum),
                MathUtil.getAverage(datum),
                MathUtil.getFluctuation(datum),
                MathUtil.getOscillation(datum),
        };
    }

    private double[][] intlifyTwoDeep(final double[][] data) {
        final var intData = new long[data.length][];
        for (int i = 0; i < data.length; i++) {
            intData[i] = new long[]{Math.round(data[i][0] * 10_000_000), Math.round(data[i][1] * 10_000_000), Math.round(data[i][2] * 10_000_000), Math.round(data[i][3] * 10_000_000), Math.round(data[i][4] * 10_000_000)};
        }
        final var doubleData = new double[data.length][];
        for (int i = 0; i < data.length; i++) {
            doubleData[i] = new double[]{intData[i][0] / 10_000.0, intData[i][1] / 10_000.0, intData[i][2] / 10_000.0, intData[i][3] / 10_000.0, intData[i][4] / 10_000.0};
        }
        return doubleData;
    }

    private double[] intlify(final double[] datum) {
        return new double[]{
                Math.round(datum[0] * 10_000_000) / 10_000.0,
                Math.round(datum[1] * 10_000_000) / 10_000.0,
                Math.round(datum[2] * 10_000_000) / 10_000.0,
                Math.round(datum[3] * 10_000_000) / 10_000.0,
                Math.round(datum[4] * 10_000_000) / 10_000.0,
        };
    }

    private void buildDTree(final boolean trainRandomForest) {
        var xArrays = new int[this.train.length][];
        for (int i = 0; i < this.train.length; i++) {
            final var array = this.train[i];
            xArrays[i] = new int[]{this.labels[i], (int) Math.round(array[0] * 2_500_000), (int) Math.round(array[1] * 2_500_000), (int) Math.round(array[2] * 2_500_000), (int) Math.round(array[3] * 2_500_000), (int) Math.round(array[4] * 2_500_000)};
        }
        var xArrayListForm = Arrays.asList(xArrays);
        Collections.shuffle(xArrayListForm);
        xArrays = xArrayListForm.toArray(new int[0][]);
        final var df = DataFrame.of(xArrays);
        log.debug("DataFrame: {}", df.toString(0, 20, true));
        this.giniTree = DecisionTree.fit(Formula.lhs("V1"), df, new DecisionTree.Options(SplitRule.GINI, 20, 0, 5));
        this.entropyTree = DecisionTree.fit(Formula.lhs("V1"), df, new DecisionTree.Options(SplitRule.ENTROPY, 20, 0, 3));

        // Shitty Forests
        if (!trainRandomForest) return;
        this.giniForest = RandomForest.fit(Formula.lhs("V1"), df, new RandomForest.Options(125, 0, SplitRule.GINI, 20, 0, 5, 1.0, null, null, null));
        this.entropyForest = RandomForest.fit(Formula.lhs("V1"), df, new RandomForest.Options(125, 0, SplitRule.ENTROPY, 20, 0, 3, 1.0, null, null, null));
    }

    /**
     * Prepares a 5-long aim double array for ML
     *
     * @param input the pre-attack input slice
     * @return the prepared input
     */
    public double[] prepareInput(final double[][] input) {
        if (!this.statistics && !this.intlify) return input[this.slice];

        if (!this.statistics) {
            return this.intlify(input[this.slice]);
        }

        if (!this.intlify) {
            return this.statistiize(input[this.slice]);
        }

        final var datum = input[this.slice];
        return new double[]{
                Math.round(MathUtil.getStandardDeviation(datum) * 10_000_000) / 10_000.0,
                Math.round(MathUtil.getSkewness(datum) * 10_000_000) / 10_000.0,
                Math.round(MathUtil.getAverage(datum) * 10_000_000) / 10_000.0,
                Math.round(MathUtil.getFluctuation(datum) * 10_000_000) / 10_000.0,
                Math.round(MathUtil.getOscillation(datum) * 10_000_000) / 10_000.0,
        };
    }

    public LogisticRegression trainLogisticRegression() {
        return LogisticRegression.fit(train, labels);
    }

    public KNN trainKNN() {
        return KNN.fit(train, labels);
    }

    public FLD trainFLD() {
        return FLD.fit(train, labels);
    }

    /**
     * Trains an Support Vector Machine
     *
     * @param c the C parameter for the vector machine. Common options are 0.001, 0.01, 0.1, 1, 10, 100 or even 1000.
     * @return the trained vector machine
     */
    public Classifier<double[]> trainSVM(final double c) {
        return SVM.fit(train, labels, new SVM.Options(c));
    }

    public LDA trainLDA() {
        return LDA.fit(train, labels);
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
            boolean shrink, Path dataDirectory
    ) throws IOException {
        List<double[][][]> legitDataList = new ArrayList<>();
        for (String name : legitDatasetNames) {
            double[][][] data = loadData(name, dataDirectory);
            if (data != null) {
                legitDataList.add(data);
            } else {
                log.error("[BetterAnticheat] Failed to load ML data from file {} (legit split), please check your ML configuration.", name);
            }
        }

        List<double[][][]> cheatingDataList = new ArrayList<>();
        for (String name : cheatingDatasetNames) {
            double[][][] data = loadData(name, dataDirectory);
            if (data != null) {
                cheatingDataList.add(data);
            } else {
                log.error("[BetterAnticheat] Failed to load ML data from file {} (cheating split), please check your ML configuration.", name);
            }
        }

        double[][][] mergedLegitData = mergeData(legitDataList);
        double[][][] mergedCheatingData = mergeData(cheatingDataList);

        final var trainer = new MLTrainer(mergedLegitData, mergedCheatingData, slice, true, intlify, statistics, modelType.toLowerCase().contains("forest"));

        return switch (modelType.toLowerCase()) {
            case "decision_tree_gini" -> {
                final var model = trainer.getGiniTree();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    double[] prepared = trainer.statistiize(input[slice]);
                    int[] tupleData = new int[prepared.length];
                    for (int i = 0; i < prepared.length; i++) {
                        tupleData[i] = (int) Math.round(prepared[i] * 2_500_000);
                    }
                    return (double) model.predict(Tuple.of(PREDICTION_STRUCT, tupleData));
                };
            }
            case "decision_tree_entropy" -> {
                final var model = trainer.getEntropyTree();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    double[] prepared = trainer.statistiize(input[slice]);
                    int[] tupleData = new int[prepared.length];
                    for (int i = 0; i < prepared.length; i++) {
                        tupleData[i] = (int) Math.round(prepared[i] * 2_500_000);
                    }
                    return (double) model.predict(Tuple.of(PREDICTION_STRUCT, tupleData));
                };
            }
            case "random_forest_gini" -> {
                final var model = trainer.getGiniForest();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    double[] prepared = trainer.statistiize(input[slice]);
                    int[] tupleData = new int[prepared.length];
                    for (int i = 0; i < prepared.length; i++) {
                        tupleData[i] = (int) Math.round(prepared[i] * 2_500_000);
                    }
                    return (double) model.predict(Tuple.of(PREDICTION_STRUCT, tupleData));
                };
            }
            case "random_forest_entropy" -> {
                final var model = trainer.getEntropyForest();
                yield (double[][] input) -> {
                    // Do not pre-intlify for decision tree-family models.
                    double[] prepared = trainer.statistiize(input[slice]);
                    int[] tupleData = new int[prepared.length];
                    for (int i = 0; i < prepared.length; i++) {
                        tupleData[i] = (int) Math.round(prepared[i] * 2_500_000);
                    }
                    return (double) model.predict(Tuple.of(PREDICTION_STRUCT, tupleData));
                };
            }
            case "logistic_regression" -> {
                final var model = trainer.trainLogisticRegression();
                yield (double[][] input) -> (double) model.predict(trainer.prepareInput(input));
            }
            case "fld" -> {
                final var model = trainer.trainFLD();
                yield (double[][] input) -> (double) model.predict(trainer.prepareInput(input));
            }
            case "knn" -> {
                final var model = trainer.trainKNN();
                yield (double[][] input) -> (double) model.predict(trainer.prepareInput(input));
            }
            case "lda" -> {
                final var model = trainer.trainLDA();
                yield (double[][] input) -> (double) model.predict(trainer.prepareInput(input));
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

    private static double[][][] loadData(String fileName, Path dataDirectory) throws IOException {
        String resourceName = fileName + ".json";
        String compressedResourceName = fileName + ".json.zst";
        InputStream resourceStream = MLTrainer.class.getClassLoader().getResourceAsStream(resourceName);
        InputStream compressedResourceStream = MLTrainer.class.getClassLoader().getResourceAsStream(compressedResourceName);

        if (compressedResourceStream != null) {
            log.debug("Loading compressed data from resource: {}", compressedResourceName);
            try (InputStream stream = compressedResourceStream) {
                byte[] compressedData = stream.readAllBytes();
                byte[] jsonData = Zstd.decompress(compressedData, (int) Zstd.decompressedSize(compressedData));
                return readData(jsonData);
            }
        } else if (resourceStream != null) {
            log.debug("Loading data from resource: {}", resourceName);
            try (InputStream stream = resourceStream) {
                byte[] jsonData = stream.readAllBytes();
                return readData(jsonData);
            }
        } else {
            log.debug("Resource '{}' not found, trying local file system.", resourceName);
            final var recordingDirectory = dataDirectory.resolve("recording");
            if (!recordingDirectory.toFile().exists()) {
                recordingDirectory.toFile().mkdirs();
            }
            final var file = recordingDirectory.resolve(resourceName);
            if (!file.toFile().exists()) {
                return null;
            }
            byte[] jsonData = Files.readAllBytes(file);
            return readData(jsonData);
        }
    }

    private static double[][][] readData(byte[] jsonData) {
        final var root = JSON.parseObject(new String(jsonData, StandardCharsets.UTF_16LE));
        JSONArray yawsArrays = root.getJSONArray("yaws");
        JSONArray offsetsArrays = root.getJSONArray("offsets");
        JSONArray enhancedOffsetsArrays = root.getJSONArray("enhancedOffsets");

        double[][] yaws = new double[yawsArrays.size()][];
        double[][] offsets = new double[offsetsArrays.size()][];
        double[][] enhancedOffsets = new double[enhancedOffsetsArrays.size()][];

        for (int i = 0; i < yawsArrays.size(); i++) {
            JSONArray yawsArray = (JSONArray) yawsArrays.get(i);
            yaws[i] = new double[yawsArray.size()];
            for (int j = 0; j < yawsArray.size(); j++) {
                yaws[i][j] = yawsArray.getDoubleValue(j);
            }
        }

        for (int i = 0; i < offsetsArrays.size(); i++) {
            JSONArray offsetsArray = (JSONArray) offsetsArrays.get(i);
            offsets[i] = new double[offsetsArray.size()];
            for (int j = 0; j < offsetsArray.size(); j++) {
                offsets[i][j] = offsetsArray.getDoubleValue(j);
            }
        }

        for (int i = 0; i < enhancedOffsetsArrays.size(); i++) {
            JSONArray enhancedOffsetsArray = (JSONArray) enhancedOffsetsArrays.get(i);
            enhancedOffsets[i] = new double[enhancedOffsetsArray.size()];
            for (int j = 0; j < enhancedOffsetsArray.size(); j++) {
                enhancedOffsets[i][j] = enhancedOffsetsArray.getDoubleValue(j);
            }
        }

        return new double[][][]{yaws, offsets, enhancedOffsets};
    }
}
