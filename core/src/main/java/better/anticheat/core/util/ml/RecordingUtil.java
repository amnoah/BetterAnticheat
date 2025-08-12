package better.anticheat.core.util.ml;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.github.luben.zstd.Zstd;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.ForyBuilder;

@Slf4j
@UtilityClass
public class RecordingUtil {
    private final ThreadSafeFory createSmallRecording = new ForyBuilder()
            .withCodegen(true)
            .withAsyncCompilation(true)
            .buildThreadSafeForyPool(2,2, 1000, TimeUnit.DAYS);

    public byte[] createSmallRecording(double[][][] data) {
        return Zstd.compress(createSmallRecording.serialize(data), 22);
    }

    public double[][][] readSmallRecording(byte[] data) {
        return (double[][][]) createSmallRecording.deserialize(Zstd.decompress(data, (int) Zstd.getFrameContentSize(data)));
    }

    public double[][][] loadData(String fileName, Path dataDirectory) throws IOException {
        final var resourceName = fileName + ".json";
        final var compressedResourceName = resourceName + ".zst";
        final var smallResourceName = fileName + ".brecord";

        // Try loading from classpath resources first
        final var compressedResourceStream = RecordingUtil.class.getClassLoader().getResourceAsStream(compressedResourceName);
        if (compressedResourceStream != null) {
            log.debug("Loading compressed data from resource: {}", compressedResourceName);
            try (InputStream stream = compressedResourceStream) {
                byte[] compressedData = stream.readAllBytes();
                byte[] jsonData = Zstd.decompress(compressedData, (int) Zstd.getFrameContentSize(compressedData));
                return readData(jsonData);
            }
        }

        final var smallResourceStream = RecordingUtil.class.getClassLoader().getResourceAsStream(smallResourceName);
        if (smallResourceStream != null) {
            log.debug("Loading small data from resource: {}", smallResourceName);
            try (InputStream stream = smallResourceStream) {
                byte[] jsonData = stream.readAllBytes();
                return readSmallRecording(jsonData);
            }
        }

        final var resourceStream = RecordingUtil.class.getClassLoader().getResourceAsStream(resourceName);
        if (resourceStream != null) {
            log.debug("Loading data from resource: {}", resourceName);
            try (InputStream stream = resourceStream) {
                byte[] jsonData = stream.readAllBytes();
                return readData(jsonData);
            }
        }

        // Fallback to local file system
        log.debug("Resource '{}' not found, trying local file system.", resourceName);
        final var recordingDirectory = dataDirectory.resolve("recording");
        if (!Files.exists(recordingDirectory)) {
            Files.createDirectories(recordingDirectory);
        }

        log.debug("Loading data for {}/{} / {}/{} / {}/{}",
                recordingDirectory.toFile().getAbsolutePath(), resourceName,
                recordingDirectory.toFile().getAbsolutePath(), compressedResourceName,
                recordingDirectory.toFile().getAbsolutePath(), smallResourceName
        );

        final var compressedFile = recordingDirectory.resolve(compressedResourceName);
        if (Files.exists(compressedFile)) {
            log.debug("Loading compressed data from file: {}", compressedFile);
            byte[] compressedData = Files.readAllBytes(compressedFile);
            byte[] jsonData = Zstd.decompress(compressedData, (int) Zstd.decompressedSize(compressedData));
            return readData(jsonData);
        }

        final var smallFile = recordingDirectory.resolve(smallResourceName);
        if (Files.exists(smallFile)) {
            log.debug("Loading small data from file: {}", smallFile);
            byte[] jsonData = Files.readAllBytes(smallFile);
            return readSmallRecording(jsonData);
        }

        final var file = recordingDirectory.resolve(resourceName);
        if (!Files.exists(file)) {
            return null;
        }
        byte[] jsonData = Files.readAllBytes(file);
        return readData(jsonData);
    }

    public double[][][] readData(byte[] jsonData) {
        JSONObject root;
        try {
            root = JSON.parseObject(new String(jsonData, StandardCharsets.UTF_16LE));
        } catch (final JSONException ignored) {
            root = JSON.parseObject(new String(jsonData, StandardCharsets.UTF_8));
        }
        final var yawsArrays = root.getJSONArray("yaws");
        final var offsetsArrays = root.getJSONArray("offsets");
        final var enhancedOffsetsArrays = root.getJSONArray("enhancedOffsets");

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
