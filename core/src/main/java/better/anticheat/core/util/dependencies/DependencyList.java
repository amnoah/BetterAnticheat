package better.anticheat.core.util.dependencies;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DependencyList {
    public static final String[] DEPENDENCIES = new String[]{
            "wtf.spare:sparej:1.0.1",
            "it.unimi.dsi:fastutil:8.5.16",
            "org.yaml:snakeyaml:2.4",
            "io.github.revxrsal:lamp.bukkit:4.0.0-rc.12",
            "io.github.revxrsal:lamp.velocity:4.0.0-rc.12",
            "io.github.revxrsal:lamp.brigadier:4.0.0-rc.12",
            "io.github.revxrsal:lamp.common:4.0.0-rc.12",
            "com.github.haifengl:smile-core:4.4.0",
            "com.github.haifengl:smile-plot:4.4.0",
            "com.github.luben:zstd-jni:1.5.6-7",
            "com.alibaba.fastjson2:fastjson2:2.0.57",
            "org.apache.fory:fory-core:0.11.2",
            // Start: Extra items because Libby is a pile of trash and does not resolve dependencies of dependencies section
            "com.github.haifengl:smile-base:4.4.0", // A dependency of smile. Update it as smile updates.
            "org.apache.commons:commons-csv:1.14.0", // A dependency of smile. Update it as smile updates.
            "org.bytedeco:javacpp:1.5.12", // A dependency of smile. Update it as smile updates.
            "org.bytedeco:openblas:0.3.30-1.5.12", // A dependency of smile. Update it as smile updates.
            "org.bytedeco:arpack-ng:3.9.1-1.5.12", // A dependency of smile. Update it as smile updates.
            "org.duckdb:duckdb_jdbc:1.3.2.0", // A dependency of smile. Update it as smile updates.
            "com.google.guava:guava:32.1.2-jre", // A dependency of FORY. Update it as FORY updates.
            // End: Extra items because Libby is a pile of trash and does not resolve dependencies of dependencies section
    };

    public static final String[][] REPOSITORIES = new String[][]{
            {"mirror", "https://german-code-repo.haldor.xyz/mirror"},
            {"cloudera", "https://repository.cloudera.com/content/repositories/public/"},
            {"atlassian-3rdp-mirror", "https://packages.atlassian.com/maven-3rdparty/"},
            {"gcs-central-mirror", "https://maven-central.storage-download.googleapis.com/maven2/"},
            {"atlassian-external-mirror", "https://packages.atlassian.com/mvn/maven-atlassian-external/"},
            {"wso2-releases", "https://maven.wso2.org/nexus/content/repositories/releases/"},
            {"wso2-public", "https://maven.wso2.org/nexus/content/repositories/public/"},
            {"spigot", "https://hub.spigotmc.org/nexus/content/repositories/snapshots/"},
            {"mulesoft", "https://repository.mulesoft.org/nexus/content/repositories/public/"},
            {"jcenter", "https://jcenter.bintray.com/"},
            {"paper", "https://repo.papermc.io/repository/maven-public/"},
            {"sonatype-mirror", "https://oss.sonatype.org/content/groups/public/"}
    };
}
