package industries.angelic.depensurer.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/** Builds small jars for the scan tests. */
final class TestJars {

    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    private final Manifest manifest = new Manifest();

    TestJars() {
        manifest.getMainAttributes()
            .put(Attributes.Name.MANIFEST_VERSION, "1.0");
    }

    TestJars entry(String name, String content) {
        entries.put(name, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    TestJars entry(String name) {
        return entry(name, name);
    }

    TestJars attribute(String key, String value) {
        manifest.getMainAttributes()
            .putValue(key, value);
        return this;
    }

    /** An mcmod.info in the 1.7.10 list format. */
    TestJars mcmod(String modId, String version) {
        return entry("mcmod.info", "[{\"modid\": \"" + modId + "\", \"version\": \"" + version + "\"}]");
    }

    /** A declaring mod's META-INF/depensurer.json with one mod and the given dep objects. */
    TestJars declares(String modId, String... deps) {
        return entry(
            "META-INF/depensurer.json",
            "{\"schema\": 1, \"mods\": [{\"modId\": \"" + modId
                + "\", \"class\": \"test."
                + modId
                + "\", \"deps\": ["
                + String.join(", ", deps)
                + "]}]}");
    }

    File write(File file) throws IOException {
        file.getParentFile()
            .mkdirs();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return file;
    }
}
