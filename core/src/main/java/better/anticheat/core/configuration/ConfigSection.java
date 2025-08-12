package better.anticheat.core.configuration;

import lombok.Getter;

import java.io.Serializable;
import java.util.*;

/**
 * This object represents a node in a configuration file. Given this node, we are able to traverse the configuration by
 * accessing children, the parent, or the root of the file. We are also able to access and modify values stored within
 * this node.
 */
public class ConfigSection {

    private final ConfigurationFile file;
    @Getter
    private final ConfigSection parent;
    @Getter
    private final String key;
    private final Map<String, Object> config;

    /**
     * Create a root node. This node has no parent as this is the first node.
     */
    public ConfigSection(ConfigurationFile file, Map<String, Object> config) {
        this.file = file;
        this.parent = null;
        this.config = config;
        key = "";
    }

    /**
     * Create a child node. We are able to traverse this node both toward children and toward the parent.
     */
    private ConfigSection(ConfigurationFile file, ConfigSection parent, Map<String, Object> config, String key) {
        this.file = file;
        this.parent = parent;
        this.config = config;
        this.key = key;
    }

    /**
     * Get the root node of this configuration file.
     */
    public ConfigSection getRoot() {
        ConfigSection section = this;
        while (section.getParent() != null) section = section.getParent();
        return section;
    }

    /**
     * Get all children ConfigSection objects.
     */
    @SuppressWarnings("unchecked")
    public Set<ConfigSection> getChildren() {
        Set<ConfigSection> children = new HashSet<>();

        for (String key : config.keySet()) {
            Object obj = config.get(key);

            // Ensure that the object we're accessing is actually a config node before we add it.
            if (!(obj instanceof Map<?, ?> map)) continue;
            if (!map.keySet().stream().allMatch(a -> a instanceof String)) continue;
            Map<String, Object> finalMap = (Map<String, Object>) map;

            children.add(new ConfigSection(file, this, finalMap, key));
        }

        return children;
    }

    /**
     * Get the node at the given path.
     * If you specify more than one path, it will recursively access the node until all paths are processed. If no node
     * exists at a given path, it will return the ConfigSection at the closest path to the end.
     * Example:
     * getConfigSection("test", "path") will return the node test.path
     */
    @SuppressWarnings("unchecked")
    public ConfigSection getConfigSection(Object... keys) {
        ConfigSection section = this;
        for (Object key : keys) {
            if (!section.hasNode(key)) return null;

            Object obj = section.config.get(key.toString());

            // Ensure that the object we're accessing is actually a config node before we grab it.
            if (!(obj instanceof Map<?, ?> map)) continue;
            if (!map.keySet().stream().allMatch(a -> a instanceof String)) continue;
            Map<String, Object> finalMap = (Map<String, Object>) map;

            section = new ConfigSection(file, section, finalMap, key.toString());
        }
        return section;
    }

    /**
     * Get the node at the given path, or create it if it isn't present.
     * If you specify more than one path, it will recursively access the node until all paths are processed. If no node
     * exists at a given path, it will return the ConfigSection at the closest path to the end.
     * Example:
     * getConfigSection("test", "path") will return the node test.path
     */
    @SuppressWarnings("unchecked")
    public ConfigSection getConfigSectionOrCreate(Object... keys) {
        ConfigSection section = this;
        for (Object key : keys) {
            if (!section.hasNode(key)) {
                section.addNode(key);
            }

            Object obj = section.config.get(key.toString());

            // Ensure that the object we're accessing is actually a config node before we grab it.
            if (!(obj instanceof Map<?, ?> map)) continue;
            if (!map.keySet().stream().allMatch(a -> a instanceof String)) continue;
            Map<String, Object> finalMap = (Map<String, Object>) map;

            section = new ConfigSection(file, section, finalMap, key.toString());
        }
        return section;
    }

    /*
     * TODO: I don't like the default value system. How can we represent this, showing that the value may not be
     *  present, while also maintaining the fact that values may actually be null in the config? My only idea is using
     *  Optionals, but my opinion of them is rather negative due to their overly verbose nature.
     */

    /**
     * Return the list at the given node. The class type input is only used to specify what the generic is.
     */
    @SuppressWarnings("unchecked")
    public <E extends Serializable> List<E> getList(Class<E> classType, String node) {
        List<E> obj;

        try {
            if (config.get(node) instanceof List) obj = (List<E>) config.get(node);
            else {
                obj = new ArrayList<>();
                obj.add((E) config.get(node));
            }
        } catch (Exception e) {
            obj = new ArrayList<>();
        }

        if (obj == null) obj = new ArrayList<>();

        return obj;
    }

    /**
     * Return the object at the given node. The class type input is only used to specify what the generic is.
     */
    public <E extends Serializable> E getObject(Class<E> classType, String node, E defaultValue) {
        E obj;

        try {
            obj = (E) config.get(node);
        } catch (Exception e) {
            obj = defaultValue;
        }

        return obj == null ? defaultValue : obj;
    }

    /**
     * Return the object at the given node. The class type input is only used to specify what the generic is. If the
     * given node isn't present, then we set it to the setValue.
     */
    @SuppressWarnings("unchecked")
    public <E extends Serializable> E getObjectOrSet(Class<E> classType, String node, E setValue) {
        E obj;

        if (hasNode(node)) {
            try {
                obj = (E) config.get(node);
            } catch (Exception e) {
                setObject(classType, node, setValue);
                obj = setValue;
            }
        } else {
            setObject(classType, node, setValue);
            obj = setValue;
        }

        return obj;
    }

    /**
     * Return whether there is any node present at the given node.
     */
    public boolean hasNode(Object node) {
        return config.get(node.toString()) != null;
    }

    /**
     * Set the list present at the given node.
     */
    public <E extends Serializable> void setList(Class<E> classType, String node, List<E> value) {
        config.put(node, value);
        file.setModified(true);
    }

    /**
     * Set the object present at the given node.
     */
    public <E extends Serializable> void setObject(Class<E> classType, String node, E object) {
        config.put(node, object);
        file.setModified(true);
    }

    /**
     * Add the config node at the given node.
     */
    public void addNode(Object key) {
        Map<String, Object> map = new LinkedHashMap<>();
        config.put(key.toString(), map);
        file.setModified(true);
    }

    /**
     * Remove the config node at the given node.
     */
    public void removeNode(Object key) {
        config.remove(key.toString());
        file.setModified(true);
    }
}