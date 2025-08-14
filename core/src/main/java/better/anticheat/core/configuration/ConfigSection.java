package better.anticheat.core.configuration;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.io.Serializable;
import java.util.*;

/**
 * This object represents a node in a configuration file. Given this node, we are able to traverse the configuration by
 * accessing children, the parent, or the root of the file. We are also able to access and modify values stored within
 * this node.
 * As of writing this comment, we are transitioning away from a proprietary snakeyaml-based system to having a wrapper
 * built around configurate-hocon to better suit our needs - specifically with comments.
 */
public class ConfigSection {

    private final ConfigurationFile file;
    @Getter
    private final ConfigSection parent;
    @Getter
    private final CommentedConfigurationNode node;

    /**
     * Create a root node. This node has no parent as this is the first node.
     */
    public ConfigSection(ConfigurationFile file, CommentedConfigurationNode node) {
        this(file, null, node);
    }

    /**
     * Create a child node. We are able to traverse this node both toward children and toward the parent.
     */
    private ConfigSection(ConfigurationFile file, ConfigSection parent, CommentedConfigurationNode node) {
        this.file = file;
        this.parent = parent;
        this.node = node;
    }

    /*
     * Node traversal.
     */

    /**
     * Get all children ConfigSection objects.
     */
    public Set<ConfigSection> getChildren() {
        Set<ConfigSection> children = new HashSet<>();
        for (CommentedConfigurationNode node : node.childrenMap().values()) {
            children.add(new ConfigSection(file, this, node));
        }
        return children;
    }

    /**
     * Get the node at the given path.
     * If you specify more than one path, it will recursively access the node until all paths are processed. If no node
     * exists at a given path, it will return the ConfigSection at the closest path to the end.
     * Example:
     * getConfigSection("test", "path") will return the node test.path if it exists.
     */
    public ConfigSection getConfigSection(@NotNull Object... keys) {
        ConfigSection section = this;
        for (Object key : keys) {
            if (!section.node.hasChild(key)) return new ConfigSection(file, section, section.node.node(key));
            section = new ConfigSection(file, section, section.node.node(key));
        }
        return section;
    }

    /**
     * Get the node at the given path. If a node is not present, it will be created.
     * If you specify more than one path, it will recursively access the node until all paths are processed.
     * Example:
     * getConfigSection("test", "path") will return the node test.path.
     */
    public ConfigSection getConfigSectionOrCreate(@NotNull Object... keys) {
        ConfigSection section = this;
        for (Object key : keys) {
            if (!section.node.hasChild(key)) file.setModified(true);
            section = new ConfigSection(file, section, section.node.node(key));
        }
        return section;
    }

    /**
     * Get the node at the given path. If a node is not present, it will be created.
     * Please note that the comment will only be added on the last node that is created.
     * If you specify more than one path, it will recursively access the node until all paths are processed.
     * Example:
     * getConfigSection("test", "path") will return the node test.path.
     */
    public ConfigSection getConfigSectionOrCreateWithComment(String comment, @NotNull Object... keys) {
        ConfigSection section = this;
        for (int i = 0; i < keys.length; i++) {
            Object key = keys[i];
            if (!section.node.hasChild(key)) {
                if (i == keys.length - 1) section.node.node(key).comment(comment);
                file.setModified(true);
            }
            section = new ConfigSection(file, section, section.node.node(key));
        }
        return section;
    }

    /**
     * Returns the key of this node.
     */
    public Object getKey() {
        return node.key();
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
     * Remove the config node at the given node.
     */
    public void removeNode(Object key) {
        node.removeChild(key);
        file.setModified(true);
    }

    /*
     * Comment handling.
     */

    /**
     * Return whether the given node has a comment.
     */
    public boolean hasComment(Object key) {
        return getComment(key) != null;
    }

    /**
     * Return the comment on the given node.
     * Will return null if no comment is present.
     */
    public String getComment(Object key) {
        return node.node(key).comment();
    }

    /**
     * Set the comment on the given node.
     * Input null for the comment if you wish to delete the comment.
     */
    public void setComment(Object key, @Nullable String comment) {
        node.node(key).comment(comment);
        file.setModified(true);
    }

    /**
     * Set the comment for the given node if it doesn't have a comment currently.
     */
    public void setCommentIfAbsent(Object key, @NotNull String comment) {
        if (!hasComment(key)) setComment(key, comment);
    }

    /*
     * Object getting handling.
     */

    /**
     * Return the list at the given node. The class type input is only used to specify what the generic is.
     * This will return an empty optional in these scenarios:
     * 1. The list in the config was not able to be serialized to the inputted class type (ie, you entered the wrong
     * class type or the user messed up the configuration).
     * 2. The node does not exist in the config.
     */
    public <E extends Serializable> Optional<List<E>> getList(Class<E> classType, Object key) {
        try {
            if (!hasNode(key)) return Optional.empty();
            List<E> list = node.node(key).getList(classType);
            return list == null ? Optional.empty() : Optional.of(list);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Return the list at the given node. The class type input is only used to specify what the generic is. If the
     * given node isn't present, then we set it to the setValue.
     */
    public <E extends Serializable> List<E> getOrSetList(Class<E> classType, Object key, List<E> setValue) {
        return getOrSetListWithComment(classType, key, setValue, null);
    }

    /**
     * Return the list at the given node. The class type input is only used to specify what the generic is. If the
     * given node isn't present, then we set it to the setValue. If we set the value, we also set the given comment on it.
     */
    public <E extends Serializable> List<E> getOrSetListWithComment(Class<E> classType, Object key, List<E> setValue, String comment) {
        Optional<List<E>> obj = getList(classType, key);
        if (obj.isEmpty()) {
            setList(classType, key, setValue);
            setComment(key, comment);
            return setValue;
        } else return obj.get();
    }

    /** Get a list of strings at the given node. */
    public Optional<List<String>> getStringList(Object key) {
        return getList(String.class, key);
    }

    /** Get a list of strings at the given node, or set it if not present. */
    public List<String> getOrSetStringList(Object key, List<String> setValue) {
        return getOrSetList(String.class, key, setValue);
    }

    /** Get a list of strings at the given node, or set it with a comment if not present. */
    public List<String> getOrSetStringListWithComment(Object key, List<String> setValue, String comment) {
        return getOrSetListWithComment(String.class, key, setValue, comment);
    }

    /**
     * Return the object at the given node. The class type input is only used to specify what the generic is.
     * This will return an empty optional in these scenarios:
     * 1. The object in the config was not able to be serialized to the inputted class type (ie, you entered the wrong
     * class type or the user messed up the configuration).
     * 2. The node does not exist in the config.
     */
    public <E extends Serializable> Optional<E> getObject(Class<E> classType, Object key) {
        try {
            if (!hasNode(key)) return Optional.empty();
            E obj = node.node(key).get(classType);
            return obj == null ? Optional.empty() : Optional.of(obj);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Return the object at the given node. The class type input is only used to specify what the generic is. If the
     * given node isn't present, then we set it to the setValue.
     */
    public <E extends Serializable> E getOrSetObject(Class<E> classType, Object key, E setValue) {
        return getOrSetObjectWithComment(classType, key, setValue, null);
    }

    /**
     * Return the object at the given node. The class type input is only used to specify what the generic is. If the
     * given node isn't present, then we set it to the setValue. If we set the value, we also set the given comment on it.
     */
    public <E extends Serializable> E getOrSetObjectWithComment(Class<E> classType, Object key, E setValue, String comment) {
        Optional<E> obj = getObject(classType, key);
        if (obj.isEmpty()) {
            setObject(classType, key, setValue);
            setComment(key, comment);
            return setValue;
        } else return obj.get();
    }

    /** Get a boolean at the given node. */
    public Optional<Boolean> getBoolean(Object key) {
        return getObject(Boolean.class, key);
    }

    /** Get a boolean at the given node, or set it if not present. */
    public boolean getOrSetBoolean(Object key, boolean setValue) {
        return getOrSetObject(Boolean.class, key, setValue);
    }

    /** Get a boolean at the given node, or set it with a comment if not present. */
    public boolean getOrSetBooleanWithComment(Object key, boolean setValue, String comment) {
        return getOrSetObjectWithComment(Boolean.class, key, setValue, comment);
    }


    /** Get a double at the given node. */
    public Optional<Double> getDouble(Object key) {
        return getObject(Double.class, key);
    }

    /** Get a double at the given node, or set it if not present. */
    public double getOrSetDouble(Object key, double setValue) {
        return getOrSetObject(Double.class, key, setValue);
    }

    /** Get a double at the given node, or set it with a comment if not present. */
    public double getOrSetDoubleWithComment(Object key, double setValue, String comment) {
        return getOrSetObjectWithComment(Double.class, key, setValue, comment);
    }


    /** Get an integer at the given node. */
    public Optional<Integer> getInteger(Object key) {
        return getObject(Integer.class, key);
    }

    /** Get an integer at the given node, or set it if not present. */
    public int getOrSetInteger(Object key, int setValue) {
        return getOrSetObject(Integer.class, key, setValue);
    }

    /** Get an integer at the given node, or set it with a comment if not present. */
    public int getOrSetIntegerWithComment(Object key, int setValue, String comment) {
        return getOrSetObjectWithComment(Integer.class, key, setValue, comment);
    }


    /** Get a string at the given node. */
    public Optional<String> getString(Object key) {
        return getObject(String.class, key);
    }

    /** Get a string at the given node, or set it if not present. */
    public String getOrSetString(Object key, String setValue) {
        return getOrSetObject(String.class, key, setValue);
    }

    /** Get a string at the given node, or set it with a comment if not present. */
    public String getOrSetStringWithComment(Object key, String setValue, String comment) {
        return getOrSetObjectWithComment(String.class, key, setValue, comment);
    }

    /*
     * Object setting handling.
     */

    /**
     * Set the list present at the given node.
     */
    public <E extends Serializable> void setList(Class<E> classType, Object key, List<E> list) {
        try {
            node.node(key).setList(classType, list);
            file.setModified(true);
        } catch (Exception ignored) {}
    }

    /** Set a list of strings at the given node. */
    public void setStringList(Object key, List<String> list) {
        setList(String.class, key, list);
    }

    /**
     * Set the object present at the given node.
     */
    public <E extends Serializable> void setObject(Class<E> classType, Object key, E object) {
        try {
            node.node(key).set(classType, object);
            file.setModified(true);
        } catch (Exception ignored) {}
    }

    /** Set a boolean at the given node. */
    public void setBoolean(Object key, boolean value) {
        setObject(Boolean.class, key, value);
    }

    /** Set a double at the given node. */
    public void setDouble(Object key, double value) {
        setObject(Double.class, key, value);
    }

    /** Set an integer at the given node. */
    public void setInteger(Object key, int value) {
        setObject(Integer.class, key, value);
    }

    /** Set a string at the given node. */
    public void setString(Object key, String value) {
        setObject(String.class, key, value);
    }

    /*
     * Misc.
     */

    /**
     * Return whether there is any node present at the given node.
     */
    public boolean hasNode(Object key) {
        return node.node(key).raw() != null;
    }
}