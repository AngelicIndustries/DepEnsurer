package industries.angelic.depensurer.api.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The checks every declaration goes through, whether it comes from the annotation processor or from gradle.properties,
 * and its form in {@code META-INF/depensurer.json}.
 */
final class DepChecker {

    static final Pattern ID = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    /** Gets the messages; the caller says which dep they're about. */
    interface Reporter {

        void error(String message);

        void warning(String message);
    }

    private DepChecker() {}

    /** Checks one dep against the others of its mod and returns its JSON form. */
    static Map<String, Object> check(DepFields dep, Map<String, DepFields> byId, Reporter report) {
        if (!ID.matcher(dep.id)
            .matches()) {
            report.error("id must be letters, digits, '_', '.' or '-'");
        }

        List<String> sha1 = new ArrayList<>();
        for (String hash : dep.sha1) {
            String normalized = hash.trim()
                .toLowerCase(Locale.ROOT);
            if (!SHA1.matcher(normalized)
                .matches()) {
                report.error("sha1 \"" + hash + "\" is not 40 hex digits");
            }
            sha1.add(normalized);
        }
        String modId = dep.modId.trim();
        if (sha1.isEmpty() && modId.isEmpty()) {
            report.error("needs sha1 or modId, otherwise no jar can match");
        }
        String versions = dep.versions.trim();
        if (!versions.isEmpty()) {
            if (modId.isEmpty()) {
                report.error("versions needs modId");
            }
            String problem = VersionRanges.check(versions);
            if (problem != null) {
                report.error("versions \"" + versions + "\" " + problem);
            }
        }

        String alternativeOf = dep.alternativeOf.trim();
        if (!alternativeOf.isEmpty()) {
            DepFields target = byId.get(alternativeOf);
            if (alternativeOf.equals(dep.id)) {
                report.error("alternativeOf can't name the dep itself");
            } else if (target == null) {
                report.error("alternativeOf names \"" + alternativeOf + "\", which this mod doesn't declare");
            } else if (!target.alternativeOf.trim()
                .isEmpty()) {
                    report.error(
                        "alternativeOf must name a primary dep, and \"" + alternativeOf
                            + "\" is an alternative itself");
                }
        }

        String slug = dep.curseForgeSlug.trim();
        boolean hasCurseForge = dep.curseForgeProject != 0 || dep.curseForgeFile != 0 || !slug.isEmpty();
        if (hasCurseForge && (dep.curseForgeProject <= 0 || dep.curseForgeFile <= 0)) {
            report.error("curseforge needs both the project and the file id");
        }
        String modrinthProject = dep.modrinthProject.trim();
        String modrinthVersion = dep.modrinthVersion.trim();
        boolean hasModrinth = !modrinthProject.isEmpty() || !modrinthVersion.isEmpty();
        if (hasModrinth && (modrinthProject.isEmpty() || modrinthVersion.isEmpty())) {
            report.error("modrinth needs both the project and the version");
        }

        List<String> urls = new ArrayList<>();
        for (String url : dep.urls) {
            String trimmed = url.trim();
            if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
                report.error("url \"" + url + "\" must start with https:// or http://");
            }
            urls.add(trimmed);
        }

        List<String> include = paths(report, "include", dep.include);
        List<String> exclude = paths(report, "exclude", dep.exclude);
        List<Object> remap = new ArrayList<>();
        for (String rule : dep.remap) {
            int arrow = rule.indexOf("->");
            if (arrow < 0 || rule.indexOf("->", arrow + 2) >= 0) {
                report.error("remap \"" + rule + "\" must look like \"from/ -> to/\"");
                continue;
            }
            String from = rule.substring(0, arrow)
                .trim();
            String to = rule.substring(arrow + 2)
                .trim();
            path(report, "remap", from);
            path(report, "remap", to);
            if (from.endsWith("/") != to.endsWith("/")) {
                report.error(
                    "remap \"" + rule + "\" must map a folder to a folder (both ending in /) or a file to a file");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("from", from);
            entry.put("to", to);
            remap.add(entry);
        }

        String side = dep.side.trim()
            .toUpperCase(Locale.ROOT);
        if (!side.equals("CLIENT") && !side.equals("BOTH")) {
            report.error("side must be CLIENT or BOTH");
        }

        String file = dep.file.trim();
        // Alternatives need no source: players get the primary's link and the full build downloads the primary
        boolean primary = alternativeOf.isEmpty();
        if (primary && !hasCurseForge && !hasModrinth && urls.isEmpty()) {
            report.warning("has no CurseForge, Modrinth or URL source: players get no download link");
        } else if (primary && sha1.isEmpty()) {
            report.warning("has no sha1: the full build won't download it");
        }
        if (!sha1.isEmpty() && file.isEmpty() && modId.isEmpty() && !hasNarrowInclude(include)) {
            report.warning(
                "has no file name, modId or include narrower than assets/**: Dep Ensurer has to hash every jar to find it");
        }
        if (hasCurseForge && file.isEmpty()) {
            report.warning("has a CurseForge file but no file name: the full build can't use the CurseForge CDN");
        }
        if (hasCurseForge && slug.isEmpty()) {
            report.warning("curseforge has no slug: players get the project page instead of the file page");
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", dep.id);
        json.put("name", dep.name);
        json.put("file", file);
        json.put("sha1", sha1);
        json.put("modId", modId);
        json.put("versions", versions);
        json.put("alternativeOf", alternativeOf);
        if (hasCurseForge) {
            Map<String, Object> cf = new LinkedHashMap<>();
            cf.put("project", dep.curseForgeProject);
            cf.put("slug", slug);
            cf.put("file", dep.curseForgeFile);
            json.put("curseforge", cf);
        }
        if (hasModrinth) {
            Map<String, Object> mr = new LinkedHashMap<>();
            mr.put("project", modrinthProject);
            mr.put("version", modrinthVersion);
            json.put("modrinth", mr);
        }
        json.put("urls", urls);
        json.put("include", include);
        json.put("exclude", exclude);
        json.put("remap", remap);
        json.put("neutralize", dep.neutralize);
        json.put("side", side);
        json.put("optional", dep.optional);
        return json;
    }

    /** The whole file: one mod and its deps, with its class when it comes from an annotation. */
    static Map<String, Object> mod(String modId, String className, List<Object> deps) {
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("modId", modId);
        mod.put("class", className);
        mod.put("deps", deps);
        return mod;
    }

    /** Same rule as Dep Ensurer's Glob.isSpecific: the literal start of a glob names two folders or more. */
    private static boolean hasNarrowInclude(List<String> include) {
        for (String glob : include) {
            int wildcard = glob.length();
            for (int i = 0; i < glob.length(); i++) {
                if (glob.charAt(i) == '*' || glob.charAt(i) == '?') {
                    wildcard = i;
                    break;
                }
            }
            String root = wildcard == glob.length() ? glob : glob.substring(0, glob.lastIndexOf('/', wildcard) + 1);
            int segments = 0;
            for (String segment : root.split("/")) {
                if (!segment.isEmpty()) {
                    segments++;
                }
            }
            if (segments >= 2) {
                return true;
            }
        }
        return false;
    }

    private static List<String> paths(Reporter report, String field, List<String> values) {
        List<String> paths = new ArrayList<>();
        for (String value : values) {
            String trimmed = value.trim();
            path(report, field, trimmed);
            paths.add(trimmed);
        }
        return paths;
    }

    private static void path(Reporter report, String field, String path) {
        if (path.isEmpty()) {
            report.error(field + " has an empty entry");
        } else if (path.startsWith("/") || path.contains("\\")) {
            report.error(field + " \"" + path + "\" must be a jar path like assets/x/, without a leading / or \\");
        }
    }
}
