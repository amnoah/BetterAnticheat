package better.anticheat.core.configuration;

import lombok.Setter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Due to present issues with JitPack, this is a copy-paste of sharkbyte-configuration's snakeyaml module.
 */

public class ConfigurationFile {

    private final String fileName;
    private final Path directoryPath, filePath;
    private final InputStream input;
    private final Yaml yaml;
    private Map<String, Object> root = null;
    @Setter
    private boolean modified;

    protected File configFile;

    /**
     * Create the ConfigurationFile object without an input stream. By default, this will
     */
    public ConfigurationFile(String fileName, Path directoryPath) {
        this(fileName, directoryPath, null);
    }

    public ConfigurationFile(String fileName, Path directoryPath, InputStream input) {
        this.fileName = fileName;
        this.directoryPath = directoryPath;
        this.input = input;
        this.filePath = directoryPath.resolve(fileName);
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        yaml = new Yaml(options);
    }

    public ConfigSection load() {
        try {
            if (!Files.exists(directoryPath)) Files.createDirectories(directoryPath);
            File configFile = filePath.toFile();

            if (!Files.exists(filePath)) {
                if (input != null) Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
                else configFile.createNewFile();
            }

            this.configFile = configFile;
        } catch(Exception e) {
            e.printStackTrace();
        }

        root = null;
        try (InputStream inputStream = Files.newInputStream(configFile.toPath())) {
            root = yaml.load(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (root == null) root = new LinkedHashMap<>();
        return new ConfigSection(this, root);
    }

    public void save() {
        if (!modified) return;
        try (PrintWriter writer = new PrintWriter(configFile)) {
            yaml.dump(root, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        modified = false;
    }

    public ConfigSection getRoot() {
        if (root == null) return load();
        return new ConfigSection(this, root);
    }
}
