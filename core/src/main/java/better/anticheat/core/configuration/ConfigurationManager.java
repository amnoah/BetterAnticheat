package better.anticheat.core.configuration;

import better.anticheat.core.BetterAnticheat;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This manager provides a centralized cache for configuration files used in the anticheat. This allows for settings to
 * be accessed at any point without needing files to be unnecessarily re-accessed.
 * <p>
 * It is noteworthy that initial loads operate on a lazy-load system. This means that configuration files will not be
 * loaded at all until they have been accessed for the first time. You may want to reconsider performing first access
 * on the netty thread as that may prevent unnecessary strain. After the initial load, you can re-cache all files by
 * calling the ConfigurationManager#reload function or a specific file by calling ConfigurationManager#reloadFile.
 * <p>
 * The last thing that is especially important to note is the "file" String. By default, BetterAnticheat will assume
 * that you are operating out of the anticheat's main directory. You can either stay here and define your file as the
 * name you'd like (ex: "config.yml") or define a path (ex: "example/folder/config.yml") if you'd like it elsewhere.
 * This system supports backtracking, allowing you to go back in path (ex: "../config.yml" to place it directly in the
 * plugins folder).
 */
public class ConfigurationManager {

    private final BetterAnticheat plugin;

    private final Map<String, InputStream> defaultMap = new HashMap<>();
    private final Map<String, ConfigurationFile> configMap = new HashMap<>();

    /**
     * Initialize the ConfigurationManager object.
     */
    public ConfigurationManager(BetterAnticheat plugin) {
        this.plugin = plugin;
    }

    /**
     * Return the configuration file at the given path.
     */
    public ConfigurationFile getConfigurationFile(String file) {
        if (configMap.containsKey(file)) return configMap.get(file);
        return registerFile(file);
    }

    /**
     * Register a default config to be loaded for a file at the given path.
     */
    public void registerDefaultConfig(String file, InputStream defaultConfig) {
        defaultMap.put(file, defaultConfig);
    }

    /**
     * Re-cache all configuration files in the system.
     */
    public void load() {
        Set<String> files = configMap.keySet();
        configMap.clear();
        for (String file : files) registerFile(file);
    }

    /**
     * Save all configuration files in the system.
     */
    public void save() {
        for (ConfigurationFile file : configMap.values()) file.save();
    }

    /**
     * Re-cache a given configuration file.
     */
    public void loadFile(String file) {
        registerFile(file);
    }

    /**
     * Load the configuration file at the given path.
     */
    private ConfigurationFile registerFile(String file) {
        String[] path = file.split("/");
        Path filePath = plugin.getDirectory();
        for (int i = 0; i < path.length - 1; i++) filePath = filePath.resolve(path[i]);
        filePath = filePath.normalize();
        InputStream defaultConfig = defaultMap.get(file);
        ConfigurationFile configFile = new ConfigurationFile(path[path.length - 1], filePath, defaultConfig);
        configMap.put(file, configFile);
        return configFile;
    }
}
