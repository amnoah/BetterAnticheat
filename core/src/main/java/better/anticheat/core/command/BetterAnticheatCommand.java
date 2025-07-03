package better.anticheat.core.command;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.DataBridge;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.PlayerManager;
import better.anticheat.core.util.MathUtil;
import better.anticheat.core.util.ml.MLTrainer;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.luben.zstd.Zstd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Range;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;
import smile.classification.Classifier;
import smile.classification.MLP;
import smile.data.Tuple;
import smile.data.measure.Measure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.plot.swing.FigurePane;
import smile.plot.swing.Grid;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@RequiredArgsConstructor
@Command({"betteranticheat", "bac", "betterac", "antispam"})
@Slf4j
public class BetterAnticheatCommand {
    private final DataBridge<?> dataBridge;
    private final Path directory;
    // Fallback kyori serializer
    private final LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.builder().hexColors().extractUrls().build();

    @Subcommand("info")
    public void help(final CommandActor actor) {
        if (!hasPermission(actor)) return;
        sendReply(actor, Component.text("BetterAnticheat v" + dataBridge.getVersion()).color(TextColor.color(0x00FF00)));
    }

    @Subcommand("recording-reset")
    public void recordingReset(final CommandActor actor) {
        if (!hasPermission(actor)) return;
        final var player = getUserFromActor(actor);
        if (player == null) return;
        player.getCmlTracker().setRecordingNow(true);
        player.getCmlTracker().getRecording().clear();
        sendReply(actor, Component.text("Recording reset, and begun!"));
    }

    @Subcommand("recording-toggle")
    public void recordingToggle(final CommandActor actor) {
        if (!hasPermission(actor)) return;
        final var player = getUserFromActor(actor);
        if (player == null) return;
        player.getCmlTracker().setRecordingNow(!player.getCmlTracker().isRecordingNow());
        sendReply(actor, Component.text("Recording " + (player.getCmlTracker().isRecordingNow() ? "enabled" : "disabled") + "!"));
    }

    @Subcommand("recording-save")
    public void recordingSave(final CommandActor actor, final String name) throws IOException {
        if (!hasPermission(actor)) return;
        final var player = getUserFromActor(actor);
        if (player == null) return;
        final var yawsArrays = new JSONArray();
        final var offsetsArrays = new JSONArray();
        final var enhancedOffsetsArrays = new JSONArray();

        for (double[][] doubles : player.getCmlTracker().getRecording()) {
            final var yawsArray = new JSONArray();
            for (double v : doubles[0]) {
                yawsArray.add(v);
            }
            yawsArrays.add(yawsArray);

            final var offsetsArray = new JSONArray();
            for (double v : doubles[1]) {
                offsetsArray.add(v);
            }
            offsetsArrays.add(offsetsArray);

            final var enhancedOffsetsArray = new JSONArray();
            for (double v : doubles[2]) {
                enhancedOffsetsArray.add(v);
            }
            enhancedOffsetsArrays.add(enhancedOffsetsArray);
        }

        final var recordingDirectory = directory.resolve("recording");
        if (!recordingDirectory.toFile().exists()) {
            recordingDirectory.toFile().mkdirs();
        }
        final var exists = recordingDirectory.resolve(name + ".json").toFile().exists();
        if (exists) {
            final var bytes = Files.readAllBytes(recordingDirectory.resolve(name + ".json"));
            final var json = JSON.parseObject(new String(bytes, StandardCharsets.UTF_16LE));
            final var oldYawsArrays = json.getJSONArray("yaws");
            final var oldOffsetsArrays = json.getJSONArray("offsets");
            final var oldEnhancedOffsetsArrays = json.getJSONArray("enhancedOffsets");

            oldYawsArrays.addAll(yawsArrays);
            oldOffsetsArrays.addAll(offsetsArrays);
            oldEnhancedOffsetsArrays.addAll(enhancedOffsetsArrays);
            json.put("yaws", oldYawsArrays);
            json.put("offsets", oldOffsetsArrays);
            json.put("enhancedOffsets", oldEnhancedOffsetsArrays);
            Files.writeString(recordingDirectory.resolve(name + ".json"), JSON.toJSONString(json), StandardCharsets.UTF_16LE);
        } else {
            final JSONObject json = new JSONObject();
            json.put("yaws", yawsArrays);
            json.put("offsets", offsetsArrays);
            json.put("enhancedOffsets", enhancedOffsetsArrays);
            Files.writeString(recordingDirectory.resolve(name + ".json"), JSON.toJSONString(json), StandardCharsets.UTF_16LE);
        }

        sendReply(actor, Component.text("Recording saved! Remember to reset!"));
    }

    @Subcommand("recording-merge")
    public void recordingMerge(final CommandActor actor, final String source1, final String source2, final String dest) throws IOException {
        if (!hasPermission(actor)) return;

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

        final var recordingDirectory = directory.resolve("recording");
        if (!recordingDirectory.toFile().exists()) {
            recordingDirectory.toFile().mkdirs();
        }

        Files.writeString(recordingDirectory.resolve(dest + ".json"), JSON.toJSONString(mergedJson), StandardCharsets.UTF_16LE);

        sendReply(actor, Component.text("Merged " + source1 + " and " + source2 + " into " + dest));
    }

    @Subcommand("recording-export")
    public void recordingExport(final CommandActor actor, final String source, @Optional String dest) throws IOException {
        if (!hasPermission(actor)) return;

        if (dest == null || dest.isEmpty()) {
            dest = source;
        }

        final var sourceJson = loadRecordingJson(source);
        if (sourceJson == null) {
            sendReply(actor, Component.text("Could not load source recording: " + source));
            return;
        }

        final var exportDirectory = directory.resolve("export");
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

    @Subcommand("recording-validate")
    public void recordingValidate(final CommandActor actor, final String legit, @Range(min = 0, max = 2) final short column, final List<String> cheating) throws IOException {
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

        final double[][][] finalCheatingData = new double[][][]{combinedYaws, combinedOffsets, combinedEnhancedOffsets};

        ForkJoinPool.commonPool().execute(() -> {
            actor.reply("--- RAW DATA: ");
            runTrainerTests(legitData, finalCheatingData, actor, column, false, false);

            actor.reply("--- PROCESSED DATA: ");
            runTrainerTests(legitData, finalCheatingData, actor, column, true, true);
        });
    }

    private void runTrainerTests(double[][][] legitData, double[][][] cheatingData, CommandActor actor, short column, boolean process, boolean enhanced) {
        final MLTrainer trainer = new MLTrainer(legitData, cheatingData, column, true, process, enhanced, true);

        final var cheatingPlot = Grid.of(new double[][][]{trainer.getCheatingTrain(), trainer.getLegitTrain()});
        var pane = new FigurePane(cheatingPlot.figure());
        try {
            pane.window();
        } catch (InterruptedException | InvocationTargetException e) {
            log.error("Error while opening window", e);
        }

        double[][] legitTestData = trainer.getLegitData();
        double[][] cheatingTestData = trainer.getCheatingData();

        actor.reply("---- Decision Tree (Gini):");
        testModelI32(trainer.getGiniTree(), legitTestData, cheatingTestData, 10, actor);
        actor.reply("---- Decision Tree (Entropy):");
        testModelI32(trainer.getEntropyTree(), legitTestData, cheatingTestData, 10, actor);

        actor.reply("---- Random Forest (Gini) - OVERFITTING WARNING:");
        testModelI32(trainer.getGiniForest(), legitTestData, cheatingTestData, 1, actor);
        actor.reply("---- Random Forest (Entropy) - OVERFITTING WARNING:");
        testModelI32(trainer.getEntropyForest(), legitTestData, cheatingTestData, 1, actor);

        actor.reply("---- Legacy Models:");
        testModel(trainer.trainLogisticRegression(), legitTestData, cheatingTestData, actor, trainer, "LogisticRegression");
        testModel(trainer.trainFLD(), legitTestData, cheatingTestData, actor, trainer, "FLD");
        testModel(trainer.trainKNN(), legitTestData, cheatingTestData, actor, trainer, "KNN");
        try {
            testModel(trainer.trainLDA(), legitTestData, cheatingTestData, actor, trainer, "LDA");
        } catch (Exception e) {
            actor.reply("Error while testing LDA: " + e.getMessage());
            log.error("Error while testing LDA: ", e);
        }
    }

    private void testModelI32(final Classifier<Tuple> model, final double[][] legitData, final double[][] finalCheatingData, final int benchSize, final CommandActor actor) {
        var threshold = 5;
        var df = new DecimalFormat("#.######");

        var legitAsLegit = 0;
        var legitAsCheating = 0;
        var legitAvg = 0.0;
        var cheatingAsLegit = 0;
        var cheatingAsCheating = 0;
        var cheatingAvg = 0.0;
        final var struct = new StructType(/*new StructField("V1", DataTypes.IntType, Measure.Percent), */new StructField("V2", DataTypes.IntType, Measure.Percent), new StructField("V3", DataTypes.IntType, Measure.Percent), new StructField("V4", DataTypes.IntType, Measure.Percent), new StructField("V5", DataTypes.IntType, Measure.Percent), new StructField("V6", DataTypes.IntType, Measure.Percent));

        for (final var legitArray : legitData) {

            final var prediction = model.predict(Tuple.of(struct, new int[]{(int) Math.round(legitArray[0] * 2_500_000), (int) Math.round(legitArray[1] * 2_500_000), (int) Math.round(legitArray[2] * 2_500_000), (int) Math.round(legitArray[3] * 2_500_000), (int) Math.round(legitArray[4] * 2_500_000)}));
            if (prediction < threshold) {
                legitAsLegit++;
            } else {
                legitAsCheating++;
            }

            legitAvg += prediction;
        }

        for (final var cheatingArray : finalCheatingData) {
            final var prediction = model.predict(Tuple.of(struct, new int[]{(int) Math.round(cheatingArray[0] * 2_500_000), (int) Math.round(cheatingArray[1] * 2_500_000), (int) Math.round(cheatingArray[2] * 2_500_000), (int) Math.round(cheatingArray[3] * 2_500_000), (int) Math.round(cheatingArray[4] * 2_500_000)}));
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
                    model.predict(Tuple.of(struct, new int[]{(int) Math.round(legitArray[0] * 2_500_000), (int) Math.round(legitArray[1] * 2_500_000), (int) Math.round(legitArray[2] * 2_500_000), (int) Math.round(legitArray[3] * 2_500_000), (int) Math.round(legitArray[4] * 2_500_000)}));
                }
                for (final var cheatingArray : finalCheatingData) {
                    model.predict(Tuple.of(struct, new int[]{(int) Math.round(cheatingArray[0] * 2_500_000), (int) Math.round(cheatingArray[1] * 2_500_000), (int) Math.round(cheatingArray[2] * 2_500_000), (int) Math.round(cheatingArray[3] * 2_500_000), (int) Math.round(cheatingArray[4] * 2_500_000)}));
                }
            }
            var end = System.currentTimeMillis();
            times[i] = end - start;
        }

        legitAvg /= legitData.length;
        cheatingAvg /= finalCheatingData.length;

        actor.reply(
                String.format(
                "Results for (%s): %d legit as legit, %d legit as cheating, %d cheating as legit, %d cheating as cheating. %s legit avg, %s cheating avg.\n" +
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
                        df.format(MathUtil.getAverage(times) / ((legitData.length + finalCheatingData.length) * benchmarkRuns))
                )
        );
    }

    private void testModel(final Classifier<double[]> model, final double[][] legitData, final double[][] finalCheatingData, final CommandActor actor, final MLTrainer trainer, final String label) {
        var threshold = model instanceof MLP ? 0.5 : 5;
        var df = new DecimalFormat("#.######");

        var legitAsLegit = 0;
        var legitAsCheating = 0;
        var legitAvg = 0.0;
        var cheatingAsLegit = 0;
        var cheatingAsCheating = 0;
        var cheatingAvg = 0.0;

        for (final var legitArray : legitData) {
            final var prepared = trainer.prepareInput(new double[][]{legitArray, legitArray, legitArray});
            final var prediction = model.predict(prepared);
            if (prediction < threshold) {
                legitAsLegit++;
            } else {
                legitAsCheating++;
            }

            legitAvg += prediction;
        }

        for (final var cheatingArray : finalCheatingData) {
            final var prepared = trainer.prepareInput(new double[][]{cheatingArray, cheatingArray, cheatingArray});
            final var prediction = model.predict(prepared);
            if (prediction < threshold) {
                cheatingAsLegit++;
            } else {
                cheatingAsCheating++;
            }

            cheatingAvg += prediction;
        }

        // Benchmark
        final var times = new double[10];
        final var benchmarkRuns = 100;
        for (int i = 0; i < times.length; i++) {
            var start = System.currentTimeMillis();
            for (int j = 0; j < benchmarkRuns; j++) {
                for (final var legitArray : legitData) {
                    model.predict(trainer.prepareInput(new double[][]{legitArray, legitArray, legitArray}));
                }
                for (final var cheatingArray : finalCheatingData) {
                    model.predict(trainer.prepareInput(new double[][]{cheatingArray, cheatingArray, cheatingArray}));
                }
            }
            var end = System.currentTimeMillis();
            times[i] = end - start;
        }

        legitAvg /= legitData.length;
        cheatingAvg /= finalCheatingData.length;

        actor.reply(
                String.format(
                        "[%s] Results for (%s): %d legit as legit, %d legit as cheating, %d cheating as legit, %d cheating as cheating. %s legit avg, %s cheating avg. \n" +
                                "Took %s ms (avg %s ms) across samples to calculate %d predictions (%s per).",
                        label,
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
                        df.format(MathUtil.getAverage(times) / ((legitData.length + finalCheatingData.length) * benchmarkRuns))
                )
        );
    }

    private @Nullable double[][][] loadData(final String name) throws IOException {
        final var recordingDirectory = directory.resolve("recording");
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
        final var recordingDirectory = directory.resolve("recording");
        if (!recordingDirectory.toFile().exists()) {
            recordingDirectory.toFile().mkdirs();
        }
        final var file = recordingDirectory.resolve(name + ".json");
        if (!file.toFile().exists()) {
            return null;
        }
        final var bytes = Files.readAllBytes(file);
        return JSON.parseObject(new String(bytes, StandardCharsets.UTF_16LE));
    }

    private double[][][] readData(final byte[] bytes) {
        final var json = JSON.parseObject(new String(bytes, StandardCharsets.UTF_16LE));
        final var yawsArrays = json.getJSONArray("yaws");
        final var offsetsArrays = json.getJSONArray("offsets");
        final var enhancedOffsetsArrays = json.getJSONArray("enhancedOffsets");

        // We need to return double[][][], where the first layer is yaws/offsets/enhancedOffsets, and the second layer is the pre-split arrays
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

        return new double[][][]{yaws, offsets, enhancedOffsets};
    }

    private void sendReply(final CommandActor actor, final ComponentLike message) {
        try {
            final var method = actor.getClass().getMethod("reply", ComponentLike.class);
            method.trySetAccessible();
            method.invoke(actor, message);
        } catch (final Exception e) {
            log.error("Failed to find reply method, is your server up to date?", e);
            actor.reply(legacyComponentSerializer.serialize(message.asComponent()));
        }
    }

    private @Nullable Player getUserFromActor(final CommandActor actor) {
        return PlayerManager.getPlayerByName(actor.name());
    }

    private boolean hasPermission(final CommandActor actor) {
        if (actor.name().equalsIgnoreCase("console")) return true;
        var user = getUserFromActor(actor);
        if (user == null) return false;
        return dataBridge.hasPermission(user.getUser(), BetterAnticheat.getInstance().getAlertPermission());
    }
}
