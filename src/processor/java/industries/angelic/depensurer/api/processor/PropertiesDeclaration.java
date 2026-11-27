package industries.angelic.depensurer.api.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes {@code META-INF/depensurer.json} from {@code depensurer.*} properties, for mods that declare their deps in
 * gradle.properties instead of with {@code @EnsureDep}. The same checks apply. depensurer.gradle.kts runs it as
 * {@code java -cp <api jar> industries.angelic.depensurer.api.processor.PropertiesDeclaration <properties> <out dir>},
 * where the properties file holds {@code modId} and the {@code depensurer.*} properties.
 */
public final class PropertiesDeclaration {

    static final String PREFIX = "depensurer.";
    private static final Set<String> FIELDS = new LinkedHashSet<>(
        Arrays.asList(
            "name",
            "file",
            "sha1",
            "modId",
            "versions",
            "alternativeOf",
            "curseforge",
            "modrinth",
            "urls",
            "include",
            "exclude",
            "remap",
            "neutralize",
            "side",
            "optional"));
    /** Not deps: the list of deps, and the Dep Ensurer version depensurer.gradle.kts uses. */
    private static final Set<String> RESERVED = new LinkedHashSet<>(Arrays.asList("deps", "version"));

    private PropertiesDeclaration() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws IOException {
        if (args.length != 2) {
            err.println("usage: PropertiesDeclaration <properties file> <output dir>");
            return 2;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get(args[0]))) {
            properties.load(in);
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String json = generate(properties, errors, warnings);
        for (String warning : warnings) {
            out.println("warning: Dep Ensurer: " + warning);
        }
        for (String error : errors) {
            err.println("error: Dep Ensurer: " + error);
        }
        if (!errors.isEmpty()) {
            return 1;
        }
        Path target = Paths.get(args[1])
            .resolve(EnsureDepProcessor.OUTPUT);
        if (json == null) {
            Files.deleteIfExists(target);
            return 0;
        }
        Files.createDirectories(target.getParent());
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        return 0;
    }

    /** The file's text, or null when no deps are declared or there were errors. */
    static String generate(Properties properties, List<String> errors, List<String> warnings) {
        Map<String, Map<String, String>> fieldsById = new TreeMap<>();
        // Sorted, so the messages come in the same order every time
        for (String key : new java.util.TreeSet<>(properties.stringPropertyNames())) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String rest = key.substring(PREFIX.length());
            if (RESERVED.contains(rest)) {
                continue;
            }
            int dot = rest.indexOf('.');
            if (dot <= 0) {
                errors.add(
                    key + " is not a Dep Ensurer property; list the deps in depensurer.deps and set their fields as depensurer.<id>.<field>");
                continue;
            }
            String id = rest.substring(0, dot);
            String field = rest.substring(dot + 1);
            if (!FIELDS.contains(field)) {
                errors.add(key + ": " + field + " is not a field; the fields are " + String.join(", ", FIELDS));
                continue;
            }
            Map<String, String> fields = fieldsById.get(id);
            if (fields == null) {
                fields = new LinkedHashMap<>();
                fieldsById.put(id, fields);
            }
            fields.put(field, properties.getProperty(key));
        }

        List<String> ids = list(properties.getProperty(PREFIX + "deps", ""));
        if (ids.isEmpty()) {
            if (!fieldsById.isEmpty()) {
                errors.add(
                    "depensurer.deps is missing; list the ids of the deps, for example depensurer.deps = "
                        + String.join(", ", fieldsById.keySet()));
            }
            return null;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                errors.add("depensurer.deps lists " + id + " twice");
            }
        }
        for (String id : fieldsById.keySet()) {
            if (!seen.contains(id)) {
                errors.add("depensurer." + id + ".* is set but " + id + " isn't in depensurer.deps");
            }
        }
        String modId = properties.getProperty("modId", "")
            .trim();
        if (modId.isEmpty()) {
            errors.add("modId is missing; set it in gradle.properties as the GTNH buildscript expects");
        }

        Map<String, DepFields> byId = new LinkedHashMap<>();
        for (String id : seen) {
            Map<String, String> fields = fieldsById.get(id);
            byId.put(id, fields(id, fields == null ? Collections.<String, String>emptyMap() : fields, errors));
        }
        List<Object> deps = new ArrayList<>();
        for (final DepFields dep : byId.values()) {
            final String prefix = "depensurer." + dep.id + ": ";
            deps.add(DepChecker.check(dep, byId, new DepChecker.Reporter() {

                @Override
                public void error(String message) {
                    errors.add(prefix + message);
                }

                @Override
                public void warning(String message) {
                    warnings.add(prefix + message);
                }
            }));
        }
        if (!errors.isEmpty()) {
            return null;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", EnsureDepProcessor.SCHEMA);
        root.put("mods", Collections.<Object>singletonList(DepChecker.mod(modId, "", deps)));
        return Json.write(root);
    }

    private static DepFields fields(String id, Map<String, String> values, List<String> errors) {
        DepFields dep = new DepFields();
        dep.id = id;
        String prefix = "depensurer." + id + ".";
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue()
                .trim();
            switch (entry.getKey()) {
                case "name":
                    dep.name = value;
                    break;
                case "file":
                    dep.file = value;
                    break;
                case "sha1":
                    dep.sha1 = list(value);
                    break;
                case "modId":
                    dep.modId = value;
                    break;
                case "versions":
                    dep.versions = value;
                    break;
                case "alternativeOf":
                    dep.alternativeOf = value;
                    break;
                case "curseforge": {
                    // project:slug:file, or project:file without a slug
                    String[] parts = value.split(":", -1);
                    try {
                        if (parts.length == 3) {
                            dep.curseForgeProject = Integer.parseInt(parts[0].trim());
                            dep.curseForgeSlug = parts[1].trim();
                            dep.curseForgeFile = Integer.parseInt(parts[2].trim());
                        } else if (parts.length == 2) {
                            dep.curseForgeProject = Integer.parseInt(parts[0].trim());
                            dep.curseForgeFile = Integer.parseInt(parts[1].trim());
                        } else {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        errors
                            .add(prefix + "curseforge must be project:slug:file, for example 223796:aether-ii:2273367");
                    }
                    break;
                }
                case "modrinth": {
                    String[] parts = value.split(":", -1);
                    if (parts.length != 2) {
                        errors.add(prefix + "modrinth must be project:version, for example YhmgMVyu:4NuI8eHN");
                    } else {
                        dep.modrinthProject = parts[0].trim();
                        dep.modrinthVersion = parts[1].trim();
                    }
                    break;
                }
                case "urls":
                    dep.urls = list(value);
                    break;
                case "include":
                    dep.include = list(value);
                    break;
                case "exclude":
                    dep.exclude = list(value);
                    break;
                case "remap":
                    dep.remap = list(value);
                    break;
                case "neutralize":
                    dep.neutralize = bool(value, prefix + "neutralize", errors);
                    break;
                case "side":
                    dep.side = value.toUpperCase(Locale.ROOT);
                    break;
                case "optional":
                    dep.optional = bool(value, prefix + "optional", errors);
                    break;
                default:
                    throw new IllegalStateException(entry.getKey());
            }
        }
        return dep;
    }

    /** Entries separated by commas or semicolons. A comma inside a URL has to be written %2C. */
    static List<String> list(String value) {
        List<String> entries = new ArrayList<>();
        for (String entry : value.split("[,;]")) {
            if (!entry.trim()
                .isEmpty()) {
                entries.add(entry.trim());
            }
        }
        return entries;
    }

    private static boolean bool(String value, String key, List<String> errors) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (!value.equalsIgnoreCase("false")) {
            errors.add(key + " must be true or false");
        }
        return false;
    }
}
