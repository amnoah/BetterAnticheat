package better.anticheat.core.command.impl;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.command.Command;
import better.anticheat.core.command.CommandInfo;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.util.MathUtil;
import better.anticheat.core.util.ml.MLTrainer;
import better.anticheat.core.util.ml.RecordingSaver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.github.luben.zstd.Zstd;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Range;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;
import smile.classification.Classifier;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.plot.swing.FigurePane;
import smile.plot.swing.Grid;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@CommandInfo(name = "recording", parent = BACCommand.class)
public class RecordingCommand extends Command {
    private final RecordingSaver recordingSaver;
    private String[] changeOthersPerms;

    public RecordingCommand(BetterAnticheat plugin, RecordingSaver recordingSaver) {
        super(plugin);
        this.recordingSaver = recordingSaver;
    }

    @Subcommand("reset")
    public void recordingReset(final CommandActor actor, @Optional final String targetPlayerName) {
        if (!hasPermission(actor))
            return;
        final var player = getPlayerFromActor(actor);
        if (player == null)
            return;

        if (targetPlayerName == null) {
            player.getCmlTracker().setRecordingNow(true);
            player.getCmlTracker().getRecording().clear();
            sendReply(actor, Component.text("Reset recording, and begun!"));
        } else {
            if (!plugin.getDataBridge().hasPermission(player.getUser(), changeOthersPerms)) {
                sendReply(actor, Component.text("You do not have permission to toggle alerts for other players.")
                        .color(TextColor.color(0xFF0000)));
                return;
            }

            final var targetPlayer = BetterAnticheat.getInstance().getPlayerManager()
                    .getPlayerByUsername(targetPlayerName);
            if (targetPlayer == null) {
                sendReply(actor, Component.text("Player '" + targetPlayerName + "' not found.")
                        .color(TextColor.color(0xFF0000)));
                return;
            }

            targetPlayer.getCmlTracker().setRecordingNow(true);
            targetPlayer.getCmlTracker().getRecording().clear();
            sendReply(actor, Component.text("Reset recording, and begun for: " + targetPlayerName));
        }
    }

    @Subcommand("toggle")
    public void recordingToggle(final CommandActor actor) {
        if (!hasPermission(actor))
            return;
        final var player = getPlayerFromActor(actor);
        if (player == null)
            return;
        player.getCmlTracker().setRecordingNow(!player.getCmlTracker().isRecordingNow());
        sendReply(actor, Component
                .text("Recording " + (player.getCmlTracker().isRecordingNow() ? "enabled" : "disabled") + "!"));
    }

    @Subcommand("save")
    public void recordingSave(final CommandActor actor, final String name, @Optional final String targetPlayerName)
            throws IOException {
        if (!hasPermission(actor))
            return;
        var player = getPlayerFromActor(actor);
        if (player == null)
            return;

        if (targetPlayerName == null) {
            sendReply(actor, Component.text("Selected player: " + player.getUser().getName()));
        } else {
            if (!plugin.getDataBridge().hasPermission(player.getUser(), changeOthersPerms)) {
                sendReply(actor, Component.text("You do not have permission to toggle alerts for other players.")
                        .color(TextColor.color(0xFF0000)));
                return;
            }

            final var targetPlayer = BetterAnticheat.getInstance().getPlayerManager()
                    .getPlayerByUsername(targetPlayerName);
            if (targetPlayer == null) {
                sendReply(actor, Component.text("Player '" + targetPlayerName + "' not found.")
                        .color(TextColor.color(0xFF0000)));
                return;
            }

            sendReply(actor, Component.text("Selected player: " + targetPlayerName));

            player = targetPlayer;
        }

        // Use the injected RecordingSaver to save player data
        recordingSaver.savePlayerData(player, name);

        sendReply(actor, Component.text("Recording saved! Remember to reset!"));
    }

    @Subcommand("merge")
    public void recordingMerge(final CommandActor actor, final String source1, final String source2, final String dest)
            throws IOException {
        if (!hasPermission(actor))
            return;

        final var json1 = loadRecordingJson(source1);
        if (json1 == null) {
            sendReply(actor, Component.text("Could not load source recording: " + source1));
            return;
        }

        final var json2 = loadRecordingJson(source2);
        if (json2 == null) {
            sendReply(actor, Component.text("Could not load source recording: " + source2));
            return;
        }

        final var yaws1 = json1.getJSONArray("yaws");
        final var offsets1 = json1.getJSONArray("offsets");
        final var enhancedOffsets1 = json1.getJSONArray("enhancedOffsets");

        final var yaws2 = json2.getJSONArray("yaws");
        final var offsets2 = json2.getJSONArray("offsets");
        final var enhancedOffsets2 = json2.getJSONArray("enhancedOffsets");

        yaws1.addAll(yaws2);
        offsets1.addAll(offsets2);
        enhancedOffsets1.addAll(enhancedOffsets2);

        final JSONObject mergedJson = new JSONObject();
        mergedJson.put("yaws", yaws1);
        mergedJson.put("offsets", offsets1);
        mergedJson.put("enhancedOffsets", enhancedOffsets1);

        final var recordingDirectory = plugin.getDirectory().resolve("recording");
        if (!recordingDirectory.toFile().exists()) {
            recordingDirectory.toFile().mkdirs();
        }

        Files.writeString(recordingDirectory.resolve(dest + ".json"), JSON.toJSONString(mergedJson),
                StandardCharsets.UTF_16LE);

        sendReply(actor, Component.text("Merged " + source1 + " and " + source2 + " into " + dest));
    }

    @Subcommand("mergefolder")
    public void recordingMergeFolder(final CommandActor actor, final String folderName, final String dest)
            throws IOException {
        if (!hasPermission(actor))
            return;

        final var recordingDirectory = plugin.getDirectory().resolve("recording");
        if (!recordingDirectory.toFile().exists()) {
            recordingDirectory.toFile().mkdirs();
        }

        final var subfolder = recordingDirectory.resolve(folderName);
        if (!subfolder.toFile().exists() || !subfolder.toFile().isDirectory()) {
            sendReply(actor, Component.text("Subfolder '" + folderName + "' does not exist or is not a directory."));
            return;
        }

        // List all JSON files in the subfolder
        final var files = subfolder.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            sendReply(actor, Component.text("No JSON files found in subfolder '" + folderName + "'."));
            return;
        }

        JSONArray mergedYaws = null;
        JSONArray mergedOffsets = null;
        JSONArray mergedEnhancedOffsets = null;

        int mergedCount = 0;
        for (final var file : files) {
            final var fileName = file.getName().substring(0, file.getName().length() - 5); // Remove .json extension
            final var json = loadRecordingJson(folderName + "/" + fileName);
            if (json == null) {
                sendReply(actor, Component.text("Warning: Could not load recording: " + fileName + " (skipping)"));
                continue;
            }

            if (mergedYaws == null) {
                // Initialize with first file's data
                mergedYaws = json.getJSONArray("yaws");
                mergedOffsets = json.getJSONArray("offsets");
                mergedEnhancedOffsets = json.getJSONArray("enhancedOffsets");
            } else {
                // Merge with existing data
                mergedYaws.addAll(json.getJSONArray("yaws"));
                mergedOffsets.addAll(json.getJSONArray("offsets"));
                mergedEnhancedOffsets.addAll(json.getJSONArray("enhancedOffsets"));
            }
            mergedCount++;
        }

        if (mergedCount == 0) {
            sendReply(actor, Component.text("No recordings could be loaded from subfolder '" + folderName + "'."));
            return;
        }

        final JSONObject mergedJson = new JSONObject();
        mergedJson.put("yaws", mergedYaws);
        mergedJson.put("offsets", mergedOffsets);
        mergedJson.put("enhancedOffsets", mergedEnhancedOffsets);

        Files.writeString(recordingDirectory.resolve(dest + ".json"), JSON.toJSONString(mergedJson),
                StandardCharsets.UTF_16LE);

        sendReply(actor, Component
                .text("Merged " + mergedCount + " recordings from folder '" + folderName + "' into '" + dest + "'"));
    }

    @Subcommand("export")
    public void recordingExport(final CommandActor actor, final String source, @Optional String dest)
            throws IOException {
        if (!hasPermission(actor))
            return;

        if (dest == null || dest.isEmpty()) {
            dest = source;
        }

        final var sourceJson = loadRecordingJson(source);
        if (sourceJson == null) {
            sendReply(actor, Component.text("Could not load source recording: " + source));
            return;
        }

        final var exportDirectory = plugin.getDirectory().resolve("export");
        if (!exportDirectory.toFile().exists()) {
            exportDirectory.toFile().mkdirs();
        }

        // Convert JSON to string and compress with zstd
        final String jsonString = JSON.toJSONString(sourceJson);
        final byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_16LE);
        final byte[] compressedBytes = Zstd.compress(jsonBytes, 22);

        // Write compressed data to export directory
        Files.write(exportDirectory.resolve(dest + ".json.zst"), compressedBytes);

        sendReply(actor, Component.text("Exported " + source + " to compressed file " + dest + ".json.zst"));
    }

    @Subcommand("compare")
    public void recordingCompare(final CommandActor actor, @Range(min = 0, max = 2) final short column,
            final String legit, final String cheating,
            final @Optional @Default("false") boolean randomForests,
            final @Optional @Default("false") boolean statistics) throws IOException {
        final var legitData = loadData(legit);
        if (legitData == null) {
            sendReply(actor, Component.text("Failed to load data for " + legit));
            return;
        }

        final var cheatingData = loadData(cheating);
        if (cheatingData == null) {
            sendReply(actor, Component.text("Failed to load data for " + cheating));
            return;
        }

        ForkJoinPool.commonPool().execute(() -> {
            actor.reply("=== CONFIGURATION COMPARISON RESULTS ===");
            actor.reply("Format: MaxDepth,NodeSize -> Accuracy%");
            actor.reply("");

            // Test different depth and node size configurations
            int[] depths = { 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90 };
            int[] nodeSizes = { 2, 3, 4, 5, 6, 8, 10 };

            actor.reply("--- GINI DECISION TREE ---");
            for (int depth : depths) {
                StringBuilder line = new StringBuilder("Depth " + depth + ": ");
                for (int nodeSize : nodeSizes) {
                    double accuracy = testConfiguration(legitData, cheatingData, column, depth, depth, nodeSize,
                            nodeSize, depth,
                            depth, nodeSize, nodeSize, "gini_tree", statistics);
                    line.append(String.format("%d,%d->%.1f%% ", depth, nodeSize, accuracy));
                }
                actor.reply(line.toString());
            }

            actor.reply("");
            actor.reply("--- ENTROPY DECISION TREE ---");
            for (int depth : depths) {
                StringBuilder line = new StringBuilder("Depth " + depth + ": ");
                for (int nodeSize : nodeSizes) {
                    double accuracy = testConfiguration(legitData, cheatingData, column, depth, depth, nodeSize,
                            nodeSize, depth,
                            depth, nodeSize, nodeSize, "entropy_tree", statistics);
                    line.append(String.format("%d,%d->%.1f%% ", depth, nodeSize, accuracy));
                }
                actor.reply(line.toString());
            }

            if (randomForests) {
                actor.reply("");
                actor.reply("--- GINI RANDOM FOREST ---");
                for (int depth : depths) {
                    StringBuilder line = new StringBuilder("Depth " + depth + ": ");
                    for (int nodeSize : nodeSizes) {
                        double accuracy = testConfiguration(legitData, cheatingData, column, 26, 27, 4,
                                3, depth, depth, nodeSize, nodeSize, "gini_forest", statistics);
                        line.append(String.format("%d,%d->%.1f%% ", depth, nodeSize, accuracy));
                    }
                    actor.reply(line.toString());
                }

                actor.reply("");
                actor.reply("--- ENTROPY RANDOM FOREST ---");
                for (int depth : depths) {
                    StringBuilder line = new StringBuilder("Depth " + depth + ": ");
                    for (int nodeSize : nodeSizes) {
                        double accuracy = testConfiguration(legitData, cheatingData, column, 26, 27, 4,
                                3, depth, depth, nodeSize, nodeSize, "entropy_forest", statistics);
                        line.append(String.format("%d,%d->%.1f%% ", depth, nodeSize, accuracy));
                    }
                    actor.reply(line.toString());
                }
            }
        });
    }

    @Subcommand("validate")
    public void recordingValidate(final CommandActor actor, final String legit,
            @Range(min = 0, max = 2) final short column, final List<String> cheating) throws IOException {
        final var legitData = loadData(legit);
        if (legitData == null) {
            sendReply(actor, Component.text("Failed to load data for " + legit));
            return;
        }

        final var cheatingDataList = new ArrayList<double[][][]>();
        for (final var cheatSet : cheating) {
            final var data = loadData(cheatSet);
            if (data == null) {
                sendReply(actor, Component.text("Failed to load data for " + cheatSet));
                return;
            }
            cheatingDataList.add(data);
        }

        // Combine cheating data
        int totalYaws = 0;
        int totalOffsets = 0;
        int totalEnhancedOffsets = 0;

        for (double[][][] data : cheatingDataList) {
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

        for (double[][][] data : cheatingDataList) {
            System.arraycopy(data[0], 0, combinedYaws, yawsIndex, data[0].length);
            yawsIndex += data[0].length;
            System.arraycopy(data[1], 0, combinedOffsets, offsetsIndex, data[1].length);
            offsetsIndex += data[1].length;
            System.arraycopy(data[2], 0, combinedEnhancedOffsets, enhancedOffsetsIndex, data[2].length);
            enhancedOffsetsIndex += data[2].length;
        }

        final double[][][] finalCheatingData = new double[][][] { combinedYaws, combinedOffsets,
                combinedEnhancedOffsets };

        ForkJoinPool.commonPool().execute(() -> {
            actor.reply("--- RAW DATA: ");
            runTrainerTests(legitData, finalCheatingData, actor, column, false, false);

            actor.reply("--- PROCESSED DATA: ");
            runTrainerTests(legitData, finalCheatingData, actor, column, true, true);
        });
    }

    @Subcommand("rate")
    public void recordingRate(final CommandActor actor,
            @Range(min = 0, max = 2) final short column,
            final String legit,
            final String cheating,
            final String candidate,
            final @Optional @Default("true") boolean processed,
            final @Optional @Default("true") boolean statistics) throws IOException {
        if (!hasPermission(actor))
            return;

        final var legitData = loadData(legit);
        if (legitData == null) {
            sendReply(actor, Component.text("Failed to load data for " + legit));
            return;
        }

        final var cheatingData = loadData(cheating);
        if (cheatingData == null) {
            sendReply(actor, Component.text("Failed to load data for " + cheating));
            return;
        }

        final var candidateData = loadData(candidate);
        if (candidateData == null) {
            sendReply(actor, Component.text("Failed to load data for " + candidate));
            return;
        }

        ForkJoinPool.commonPool().execute(() -> {
            try {
                final MLTrainer trainer = new MLTrainer(legitData, cheatingData, column, false, statistics, processed);

                // Select the correct struct size based on statistics flag
                final StructType treeStructType = statistics ? MLTrainer.PREDICTION_STRUCT_XL
                        : MLTrainer.PREDICTION_STRUCT;

                // Build list of models to evaluate
                final List<String> modelTypes = new ArrayList<>();
                modelTypes.add("gini_tree");
                modelTypes.add("entropy_tree");
                modelTypes.add("gini_forest");
                modelTypes.add("entropy_forest");

                actor.reply("=== Candidate rating for '" + candidate + "' vs legit='" + legit + "', cheat='" + cheating
                        + "' (slice " + column + ", processed=" + processed + ", statistics=" + statistics + ") ===");

                double sumPercent = 0.0;
                int counted = 0;

                for (String modelType : modelTypes) {
                    Classifier<Tuple> model;
                    switch (modelType) {
                        case "gini_tree" -> model = trainer.getGiniTree();
                        case "entropy_tree" -> model = trainer.getEntropyTree();
                        case "gini_forest" -> model = trainer.getGiniForest();
                        case "entropy_forest" -> model = trainer.getEntropyForest();
                        default -> throw new IllegalStateException("Unknown model type: " + modelType);
                    }

                    // Collect candidate arrays using the selected slice
                    final double[][] candidateSlice = candidateData[column];

                    int asCheat = 0;
                    int total = candidateSlice.length;
                    final int threshold = 5;

                    for (final var sample : candidateSlice) {
                        final var wrapped = new double[3][];
                        wrapped[trainer.getSlice()] = sample;

                        final int prediction = model.predict(Tuple.of(
                                treeStructType,
                                trainer.prepareInputForTree(wrapped)));

                        if (prediction >= threshold)
                            asCheat++;
                    }

                    double percentCheat = total == 0 ? 0.0 : (asCheat * 100.0 / total);
                    sumPercent += percentCheat;
                    counted++;

                    actor.reply(String.format("%s: %d/%d (%.2f%%) classified as cheating",
                            model.getClass().getSimpleName(), asCheat, total, percentCheat));
                }

                if (counted > 0) {
                    double average = sumPercent / counted;
                    actor.reply(String.format("Average cheat rating across models: %.2f%%", average));
                }
            } catch (Throwable t) {
                log.error("Error during recordingRate execution", t);
                actor.reply("Error during rating: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        });
    }

    private void runTrainerTests(double[][][] legitData, double[][][] cheatingData, CommandActor actor, short column,
            boolean process, boolean statistics) {
        final MLTrainer trainer = new MLTrainer(legitData, cheatingData, column, true, statistics, true);

        final var cheatingPlot = Grid.of(new double[][][] { trainer.getCheatingTrain(), trainer.getLegitTrain() });
        var pane = new FigurePane(cheatingPlot.figure());
        try {
            pane.window();
        } catch (InterruptedException | InvocationTargetException e) {
            plugin.getDataBridge().logWarning("Error while opening window: " + e);
        }

        double[][] legitTestData = trainer.getLegitData();
        double[][] cheatingTestData = trainer.getCheatingData();

        actor.reply("---- Decision Tree (Gini):");
        testModelI32(trainer.getGiniTree(), legitTestData, cheatingTestData, 6, trainer, actor);
        actor.reply("---- Decision Tree (Entropy):");
        testModelI32(trainer.getEntropyTree(), legitTestData, cheatingTestData, 6, trainer, actor);

        actor.reply("---- Random Forest (Gini) - OVERFITTING WARNING:");
        testModelI32(trainer.getGiniForest(), legitTestData, cheatingTestData, 1, trainer, actor);
        actor.reply("---- Random Forest (Entropy) - OVERFITTING WARNING:");
        testModelI32(trainer.getEntropyForest(), legitTestData, cheatingTestData, 1, trainer, actor);

    }

    private void testModelI32(final Classifier<Tuple> model, final double[][] legitData,
            final double[][] finalCheatingData, final int benchSize, final MLTrainer trainer,
            final CommandActor actor) {
        var threshold = 5;
        var df = new DecimalFormat("#.######");

        var legitAsLegit = 0;
        var legitAsCheating = 0;
        var legitAvg = 0.0;
        var cheatingAsLegit = 0;
        var cheatingAsCheating = 0;
        var cheatingAvg = 0.0;
        final var struct = MLTrainer.PREDICTION_STRUCT;

        for (final var legitArray : legitData) {
            final var wrappedValidationData = new double[3][];
            wrappedValidationData[trainer.getSlice()] = legitArray;

            final var prediction = model.predict(Tuple.of(
                    struct,
                    trainer.prepareInputForTree(wrappedValidationData)));
            if (prediction < threshold) {
                legitAsLegit++;
            } else {
                legitAsCheating++;
            }

            legitAvg += prediction;
        }

        for (final var cheatingArray : finalCheatingData) {
            final var wrappedValidationData = new double[3][];
            wrappedValidationData[trainer.getSlice()] = cheatingArray;

            final var prediction = model.predict(Tuple.of(
                    struct,
                    trainer.prepareInputForTree(wrappedValidationData)));

            if (prediction < threshold) {
                cheatingAsLegit++;
            } else {
                cheatingAsCheating++;
            }

            cheatingAvg += prediction;
        }

        // Benchmark
        final var times = new double[benchSize];
        final var benchmarkRuns = 80;
        for (int i = 0; i < times.length; i++) {
            var start = System.currentTimeMillis();
            for (int j = 0; j < benchmarkRuns; j++) {
                for (final var legitArray : legitData) {
                    model.predict(Tuple.of(struct, new int[] { (int) Math.round(legitArray[0] * 2_500_000),
                            (int) Math.round(legitArray[1] * 2_500_000), (int) Math.round(legitArray[2] * 2_500_000),
                            (int) Math.round(legitArray[3] * 2_500_000), (int) Math.round(legitArray[4] * 2_500_000), 0,
                            0, 0, 0, 0 }));
                }
                for (final var cheatingArray : finalCheatingData) {
                    model.predict(Tuple.of(struct,
                            new int[] { (int) Math.round(cheatingArray[0] * 2_500_000),
                                    (int) Math.round(cheatingArray[1] * 2_500_000),
                                    (int) Math.round(cheatingArray[2] * 2_500_000),
                                    (int) Math.round(cheatingArray[3] * 2_500_000),
                                    (int) Math.round(cheatingArray[4] * 2_500_000), 0, 0, 0, 0, 0 }));
                }
            }
            var end = System.currentTimeMillis();
            times[i] = end - start;
        }

        legitAvg /= legitData.length;
        cheatingAvg /= finalCheatingData.length;

        actor.reply(
                String.format(
                        "Results for (%s): %d legit as legit, %d legit as cheating, %d cheating as legit, %d cheating as cheating. %s legit avg, %s cheating avg.\n"
                                +
                                "Took %s ms (avg %s ms) across samples to calculate %d predictions (%s per).",
                        model.getClass().getSimpleName(),
                        legitAsLegit,
                        legitAsCheating,
                        cheatingAsLegit,
                        cheatingAsCheating,
                        df.format(legitAvg),
                        df.format(cheatingAvg),
                        Arrays.toString(times),
                        df.format(MathUtil.getAverage(times)),
                        (legitData.length + finalCheatingData.length) * benchmarkRuns,
                        df.format(MathUtil.getAverage(times)
                                / ((legitData.length + finalCheatingData.length) * benchmarkRuns))));
    }

    private @Nullable double[][][] loadData(final String name) throws IOException {
        final var recordingDirectory = plugin.getDirectory().resolve("recording");
        if (!recordingDirectory.toFile().exists()) {
            recordingDirectory.toFile().mkdirs();
        }
        final var exists = recordingDirectory.resolve(name + ".json").toFile().exists();
        if (!exists) {
            return null;
        }
        final var bytes = Files.readAllBytes(recordingDirectory.resolve(name + ".json"));

        return readData(bytes);
    }

    private @Nullable JSONObject loadRecordingJson(final String name) throws IOException {
        final var recordingDirectory = plugin.getDirectory().resolve("recording");
        if (!recordingDirectory.toFile().exists()) {
            recordingDirectory.toFile().mkdirs();
        }
        final var file = recordingDirectory.resolve(name + ".json");
        if (!file.toFile().exists()) {
            return null;
        }
        final var bytes = Files.readAllBytes(file);
        try {
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_16LE));
        } catch (JSONException e) {
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private double[][][] readData(final byte[] bytes) {
        final var json = JSON.parseObject(new String(bytes, StandardCharsets.UTF_16LE));
        final var yawsArrays = json.getJSONArray("yaws");
        final var offsetsArrays = json.getJSONArray("offsets");
        final var enhancedOffsetsArrays = json.getJSONArray("enhancedOffsets");

        // We need to return double[][][], where the first layer is
        // yaws/offsets/enhancedOffsets, and the second layer is the pre-split arrays
        final var yaws = new double[yawsArrays.size()][];
        final var offsets = new double[offsetsArrays.size()][];
        final var enhancedOffsets = new double[enhancedOffsetsArrays.size()][];

        for (int i = 0; i < yawsArrays.size(); i++) {
            final var yawsArray = (JSONArray) yawsArrays.get(i);
            yaws[i] = new double[yawsArray.size()];
            for (int j = 0; j < yawsArray.size(); j++) {
                yaws[i][j] = yawsArray.getDoubleValue(j);
            }
        }

        for (int i = 0; i < offsetsArrays.size(); i++) {
            final var offsetsArray = (JSONArray) offsetsArrays.get(i);
            offsets[i] = new double[offsetsArray.size()];
            for (int j = 0; j < offsetsArray.size(); j++) {
                offsets[i][j] = offsetsArray.getDoubleValue(j);
            }
        }

        for (int i = 0; i < enhancedOffsetsArrays.size(); i++) {
            final var enhancedOffsetsArray = (JSONArray) enhancedOffsetsArrays.get(i);
            enhancedOffsets[i] = new double[enhancedOffsetsArray.size()];
            for (int j = 0; j < enhancedOffsetsArray.size(); j++) {
                enhancedOffsets[i][j] = enhancedOffsetsArray.getDoubleValue(j);
            }
        }

        return new double[][][] { yaws, offsets, enhancedOffsets };
    }

    private double testConfiguration(double[][][] legitData, double[][][] cheatingData, short column,
            int giniMaxDepth, int entropyMaxDepth, int giniNodeSize, int entropyNodeSize,
            int giniForestMaxDepth, int entropyForestMaxDepth, int giniForestNodeSize, int entropyForestNodeSize,
            String modelType, boolean statistics) {
        try {
            final MLTrainer trainer = new MLTrainer(legitData, cheatingData, column, false, true,
                    modelType.contains("forest"),
                    giniMaxDepth, entropyMaxDepth, giniNodeSize, entropyNodeSize,
                    giniForestMaxDepth, entropyForestMaxDepth, giniForestNodeSize, entropyForestNodeSize);

            double[][] legitTestData = trainer.getLegitData();
            double[][] cheatingTestData = trainer.getCheatingData();

            int correctPredictions = 0;
            int totalPredictions = legitTestData.length + cheatingTestData.length;

            final StructType treeStructType = statistics ? MLTrainer.PREDICTION_STRUCT_XL : MLTrainer.PREDICTION_STRUCT;
            final Classifier<smile.data.Tuple> model = switch (modelType) {
                case "gini_tree" -> trainer.getGiniTree();
                case "entropy_tree" -> trainer.getEntropyTree();
                case "gini_forest" -> trainer.getGiniForest();
                case "entropy_forest" -> trainer.getEntropyForest();
                case null, default -> throw new IllegalStateException("Unknown model type: " + modelType);
            };

            // Test legit data (should predict < 5)
            for (final var legitArray : legitTestData) {
                final var wrappedValidationData = new double[][] { legitArray, legitArray, legitArray };

                final var prediction = model.predict(Tuple.of(
                        treeStructType,
                        trainer.prepareInputForTree(wrappedValidationData)));

                if (prediction < 5)
                    correctPredictions++;
            }

            // Test cheating data (should predict >= 5)
            for (final var cheatingArray : cheatingTestData) {
                final var wrappedValidationData = new double[][] { cheatingArray, cheatingArray, cheatingArray };

                final var prediction = model.predict(Tuple.of(
                        treeStructType,
                        trainer.prepareInputForTree(wrappedValidationData)));

                if (prediction >= 5)
                    correctPredictions++;
            }

            return (double) correctPredictions / totalPredictions * 100.0;
        } catch (Exception e) {
            log.error("Error while testing configuration: ", e);
            return 0.0; // Return 0% accuracy if there's an error
        }
    }

    @Override
    public boolean load(ConfigSection section) {
        boolean modified = super.load(section);

        if (!section.hasNode("change-others-permissions")) {
            List<String> defaultOthers = new ArrayList<>();
            defaultOthers.add("better.anticheat.alerts.others");
            defaultOthers.add("example.permission.node");
            section.setList(String.class, "change-others-permissions", defaultOthers);
        }
        List<String> changeOthersPermsList = section.getList(String.class, "change-others-permissions");
        changeOthersPerms = new String[changeOthersPermsList.size()];
        for (int i = 0; i < changeOthersPermsList.size(); i++)
            changeOthersPerms[i] = changeOthersPermsList.get(i);

        return modified;
    }
}
