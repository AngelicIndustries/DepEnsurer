package industries.angelic.depensurer.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import industries.angelic.depensurer.util.Glob;

/** What a candidate jar declares about itself, read in one pass over the jar. */
final class JarInfo {

    private static final Pattern TOML_KEY = Pattern.compile("^\\s*(modId|version)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern TOML_MODS_TABLE = Pattern.compile("^\\[\\[\\s*mods\\s*]]");

    /** Mod id to version, from mcmod.info, mods.toml, neoforge.mods.toml, fabric.mod.json or quilt.mod.json. */
    final Map<String, String> mods;
    /** FMLCorePlugin, TweakClass or FMLAT in the manifest: FML acts on those as soon as it examines the jar. */
    final boolean earlyHooks;
    /** The asked roots that have an entry. */
    final Set<String> rootHits;
    /** The asked globs that match an entry. */
    final Set<String> globHits;

    private JarInfo(Map<String, String> mods, boolean earlyHooks, Set<String> rootHits, Set<String> globHits) {
        this.mods = mods;
        this.earlyHooks = earlyHooks;
        this.rootHits = rootHits;
        this.globHits = globHits;
    }

    /**
     * @param roots        folders or files to look for, see {@link Glob#root()}
     * @param globsByModId globs to check, only for the mod ids the jar turns out to have
     */
    static JarInfo read(File file, Collection<String> roots, Map<String, List<Glob>> globsByModId) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Manifest manifest = manifest(zip);
            Map<String, String> mods = new LinkedHashMap<>();
            readMcmodInfo(zip, mods);
            readToml(zip, "META-INF/mods.toml", manifest, mods);
            readToml(zip, "META-INF/neoforge.mods.toml", manifest, mods);
            readFabric(zip, mods);

            Set<Glob> globs = new HashSet<>();
            for (String modId : mods.keySet()) {
                List<Glob> forMod = globsByModId.get(modId);
                if (forMod != null) {
                    globs.addAll(forMod);
                }
            }
            Set<String> rootHits = new HashSet<>();
            Set<String> globHits = new HashSet<>();
            if (!roots.isEmpty() || !globs.isEmpty()) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    for (String root : roots) {
                        if (name.startsWith(root)) {
                            rootHits.add(root);
                        }
                    }
                    for (Glob glob : globs) {
                        if (!globHits.contains(glob.glob) && glob.matches(name)) {
                            globHits.add(glob.glob);
                        }
                    }
                }
            }
            return new JarInfo(mods, hasEarlyHooks(manifest), rootHits, globHits);
        }
    }

    static JarInfo empty() {
        return new JarInfo(
            Collections.<String, String>emptyMap(),
            false,
            Collections.<String>emptySet(),
            Collections.<String>emptySet());
    }

    private static Manifest manifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return new Manifest(in);
        }
    }

    private static boolean hasEarlyHooks(Manifest manifest) {
        if (manifest == null) {
            return false;
        }
        Attributes attributes = manifest.getMainAttributes();
        return attributes.getValue("FMLCorePlugin") != null || attributes.getValue("TweakClass") != null
            || attributes.getValue("FMLAT") != null;
    }

    private static JsonElement json(ZipFile zip, String path) {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        } catch (Exception e) {
            // Broken metadata is common in old jars; the jar can still match by sha1
            return null;
        }
    }

    private static void readMcmodInfo(ZipFile zip, Map<String, String> mods) {
        JsonElement root = json(zip, "mcmod.info");
        JsonArray list = null;
        if (root != null && root.isJsonArray()) {
            list = root.getAsJsonArray();
        } else if (root != null && root.isJsonObject()
            && root.getAsJsonObject()
                .has("modList")
            && root.getAsJsonObject()
                .get("modList")
                .isJsonArray()) {
                    list = root.getAsJsonObject()
                        .getAsJsonArray("modList");
                }
        if (list == null) {
            return;
        }
        for (JsonElement element : list) {
            if (element.isJsonObject()) {
                put(mods, string(element.getAsJsonObject(), "modid"), string(element.getAsJsonObject(), "version"));
            }
        }
    }

    private static void readFabric(ZipFile zip, Map<String, String> mods) {
        JsonElement fabric = json(zip, "fabric.mod.json");
        if (fabric != null && fabric.isJsonObject()) {
            put(mods, string(fabric.getAsJsonObject(), "id"), string(fabric.getAsJsonObject(), "version"));
        }
        JsonElement quilt = json(zip, "quilt.mod.json");
        if (quilt != null && quilt.isJsonObject()
            && quilt.getAsJsonObject()
                .has("quilt_loader")) {
            JsonElement loader = quilt.getAsJsonObject()
                .get("quilt_loader");
            if (loader.isJsonObject()) {
                put(mods, string(loader.getAsJsonObject(), "id"), string(loader.getAsJsonObject(), "version"));
            }
        }
    }

    /** Reads modId and version pairs from the [[mods]] tables of a Forge or NeoForge mods.toml. */
    private static void readToml(ZipFile zip, String path, Manifest manifest, Map<String, String> mods)
        throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            return;
        }
        String jarVersion = manifest == null ? ""
            : String.valueOf(
                manifest.getMainAttributes()
                    .getValue("Implementation-Version"));
        String modId = null;
        String version = "";
        boolean inMods = false;
        String text;
        try (InputStream in = zip.getInputStream(entry)) {
            text = new String(readAll(in), StandardCharsets.UTF_8);
        }
        for (String line : text.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                put(mods, modId, version);
                modId = null;
                version = "";
                inMods = TOML_MODS_TABLE.matcher(trimmed)
                    .find();
                continue;
            }
            Matcher matcher = TOML_KEY.matcher(line);
            if (!inMods || !matcher.find()) {
                continue;
            }
            if (matcher.group(1)
                .equals("modId")) {
                modId = matcher.group(2);
            } else {
                version = matcher.group(2)
                    .equals("${file.jarVersion}") ? jarVersion : matcher.group(2);
            }
        }
        put(mods, modId, version);
    }

    private static void put(Map<String, String> mods, String modId, String version) {
        if (modId != null && !modId.trim()
            .isEmpty() && !mods.containsKey(modId.trim())) {
            mods.put(modId.trim(), version == null || version.equals("null") ? "" : version.trim());
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
