package better.anticheat.core.configuration;

import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/*
 * Due to present issues with JitPack, this is a copy-paste of sharkbyte-configuration's snakeyaml module.
 */

public class ConfigurationFile {

    private final String fileName;
    private final Path directoryPath, filePath;
    private final InputStream input;
    private ConfigSection root = null;
    private HoconConfigurationLoader loader = null;
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

        loader = HoconConfigurationLoader.builder().path(filePath).build();
        try {
            root = new ConfigSection(this, loader.load());
        } catch (Exception e) {
            e.printStackTrace();
            root = null;
        }

        return root;
    }

    public void save() {
        if (!modified) return;
        try {
            loader.save(root.getNode());
        } catch (Exception e) {
            e.printStackTrace();
        }
        modified = false;
    }

    public @Nullable ConfigSection getRoot() {
        if (root == null) return load();
        return root;
    }
}
