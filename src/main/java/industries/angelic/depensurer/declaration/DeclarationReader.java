package industries.angelic.depensurer.declaration;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import industries.angelic.depensurer.api.EnsureDep;

/**
 * Reads {@code META-INF/depensurer.json} as the annotation processor writes it. Fields missing from an older schema
 * get the annotation's defaults.
 */
public final class DeclarationReader {

    public static final String PATH = "META-INF/depensurer.json";
    public static final int SCHEMA = 1;

    private DeclarationReader() {}

    public static List<ModDeclaration> read(Reader reader, String source) throws DeclarationException {
        JsonObject root;
        try {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                throw new DeclarationException(source + ": " + PATH + " is not a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new DeclarationException(source + ": " + PATH + " is not valid JSON: " + e.getMessage(), e);
        }

        try {
            int schema = root.has("schema") ? root.get("schema")
                .getAsInt() : 0;
            if (schema < 1 || schema > SCHEMA) {
                throw new DeclarationException(
                    source + ": " + PATH + " has schema " + schema + ", this Dep Ensurer reads 1 to " + SCHEMA);
            }
            List<ModDeclaration> mods = new ArrayList<>();
            for (JsonElement modElement : array(root, "mods")) {
                JsonObject mod = modElement.getAsJsonObject();
                List<DepDeclaration> deps = new ArrayList<>();
                String modId = string(mod, "modId");
                for (JsonElement depElement : array(mod, "deps")) {
                    deps.add(dep(depElement.getAsJsonObject(), source + ": " + PATH + ": mod " + modId));
                }
                mods.add(new ModDeclaration(modId, string(mod, "class"), source, deps));
            }
            return mods;
        } catch (IllegalStateException | ClassCastException | UnsupportedOperationException | NumberFormatException e) {
            throw new DeclarationException(source + ": " + PATH + " has an unexpected shape: " + e, e);
        }
    }

    private static DepDeclaration dep(JsonObject dep, String where) throws DeclarationException {
        String id = string(dep, "id");
        if (id.isEmpty()) {
            throw new DeclarationException(where + " has a dep without an id");
        }

        DepDeclaration.CurseForge curseforge = null;
        if (dep.has("curseforge")) {
            JsonObject cf = dep.getAsJsonObject("curseforge");
            curseforge = new DepDeclaration.CurseForge(integer(cf, "project"), string(cf, "slug"), integer(cf, "file"));
        }
        DepDeclaration.Modrinth modrinth = null;
        if (dep.has("modrinth")) {
            JsonObject mr = dep.getAsJsonObject("modrinth");
            modrinth = new DepDeclaration.Modrinth(string(mr, "project"), string(mr, "version"));
        }
        List<DepDeclaration.Remap> remap = new ArrayList<>();
        for (JsonElement element : array(dep, "remap")) {
            JsonObject rule = element.getAsJsonObject();
            remap.add(new DepDeclaration.Remap(string(rule, "from"), string(rule, "to")));
        }
        List<String> sha1 = new ArrayList<>();
        for (String hash : strings(dep, "sha1")) {
            sha1.add(hash.toLowerCase(Locale.ROOT));
        }
        List<String> include = dep.has("include") ? strings(dep, "include") : defaultInclude();

        String side = string(dep, "side");
        EnsureDep.Side parsedSide;
        try {
            parsedSide = side.isEmpty() ? EnsureDep.Side.CLIENT : EnsureDep.Side.valueOf(side);
        } catch (IllegalArgumentException e) {
            throw new DeclarationException(where + ", dep " + id + " has an unknown side " + side, e);
        }

        return new DepDeclaration(
            id,
            string(dep, "name"),
            string(dep, "file"),
            sha1,
            string(dep, "modId"),
            string(dep, "versions"),
            string(dep, "alternativeOf"),
            curseforge,
            modrinth,
            strings(dep, "urls"),
            include,
            strings(dep, "exclude"),
            remap,
            bool(dep, "neutralize", true),
            parsedSide,
            bool(dep, "optional", false));
    }

    private static List<String> defaultInclude() {
        List<String> include = new ArrayList<>();
        include.add("assets/**");
        return include;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key)
            .isJsonNull() ? object.get(key)
                .getAsString()
                .trim() : "";
    }

    private static int integer(JsonObject object, String key) {
        return object.has(key) ? object.get(key)
            .getAsInt() : 0;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key)
            .getAsBoolean() : fallback;
    }

    private static List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array(object, key)) {
            values.add(
                element.getAsString()
                    .trim());
        }
        return values;
    }
}
