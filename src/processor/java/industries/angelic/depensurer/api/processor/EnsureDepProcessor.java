package industries.angelic.depensurer.api.processor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import industries.angelic.depensurer.api.CurseForgeFile;
import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.api.EnsureDeps;
import industries.angelic.depensurer.api.ModrinthFile;

/**
 * Checks {@link EnsureDep} values and writes them to {@code META-INF/depensurer.json}. Dep Ensurer's coremod reads
 * that file before FML scans mods.
 */
@SupportedAnnotationTypes({ "industries.angelic.depensurer.api.EnsureDep",
    "industries.angelic.depensurer.api.EnsureDeps" })
public class EnsureDepProcessor extends AbstractProcessor {

    public static final String OUTPUT = "META-INF/depensurer.json";
    public static final int SCHEMA = 1;

    private static final String MOD_ANNOTATION = "cpw.mods.fml.common.Mod";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    // By class name, so the output doesn't depend on the order javac hands out elements
    private final Map<String, Map<String, Object>> mods = new TreeMap<>();
    private final List<Element> origins = new ArrayList<>();
    private boolean failed;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<Element> annotated = new LinkedHashSet<>();
        annotated.addAll(roundEnv.getElementsAnnotatedWith(EnsureDep.class));
        annotated.addAll(roundEnv.getElementsAnnotatedWith(EnsureDeps.class));
        for (Element element : annotated) {
            handle((TypeElement) element);
        }
        if (roundEnv.processingOver() && !mods.isEmpty() && !failed) {
            write();
        }
        return true;
    }

    private void handle(TypeElement type) {
        String className = type.getQualifiedName()
            .toString();
        String modId = modIdOf(type);
        if (modId == null) {
            error(type, "@EnsureDep must be on the @Mod class, and " + className + " has no @Mod");
            return;
        }

        EnsureDep[] deps = type.getAnnotationsByType(EnsureDep.class);
        Map<String, EnsureDep> byId = new LinkedHashMap<>();
        for (EnsureDep dep : deps) {
            if (byId.put(dep.id(), dep) != null) {
                error(type, "two @EnsureDep on " + className + " have the id \"" + dep.id() + "\"");
            }
        }

        List<Object> depList = new ArrayList<>();
        for (EnsureDep dep : deps) {
            depList.add(toJson(type, dep, byId));
        }
        Map<String, Object> mod = new LinkedHashMap<>();
        mod.put("modId", modId);
        mod.put("class", className);
        mod.put("deps", depList);
        mods.put(className, mod);
        origins.add(type);
    }

    private static String modIdOf(TypeElement type) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            TypeElement annotationType = (TypeElement) mirror.getAnnotationType()
                .asElement();
            if (!annotationType.getQualifiedName()
                .contentEquals(MOD_ANNOTATION)) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> value : mirror.getElementValues()
                .entrySet()) {
                if (value.getKey()
                    .getSimpleName()
                    .contentEquals("modid")) {
                    return String.valueOf(
                        value.getValue()
                            .getValue());
                }
            }
            return "";
        }
        return null;
    }

    private Map<String, Object> toJson(TypeElement type, EnsureDep dep, Map<String, EnsureDep> byId) {
        Checker check = new Checker(type, dep.id());
        if (!ID.matcher(dep.id())
            .matches()) {
            check.error("id must be letters, digits, '_', '.' or '-'");
        }

        List<String> sha1 = new ArrayList<>();
        for (String hash : dep.sha1()) {
            String normalized = hash.trim()
                .toLowerCase(Locale.ROOT);
            if (!SHA1.matcher(normalized)
                .matches()) {
                check.error("sha1 \"" + hash + "\" is not 40 hex digits");
            }
            sha1.add(normalized);
        }
        String modId = dep.modId()
            .trim();
        if (sha1.isEmpty() && modId.isEmpty()) {
            check.error("needs sha1 or modId, otherwise no jar can match");
        }
        String versions = dep.versions()
            .trim();
        if (!versions.isEmpty()) {
            if (modId.isEmpty()) {
                check.error("versions needs modId");
            }
            String problem = VersionRanges.check(versions);
            if (problem != null) {
                check.error("versions \"" + versions + "\" " + problem);
            }
        }

        String alternativeOf = dep.alternativeOf()
            .trim();
        if (!alternativeOf.isEmpty()) {
            EnsureDep target = byId.get(alternativeOf);
            if (alternativeOf.equals(dep.id())) {
                check.error("alternativeOf can't name the dep itself");
            } else if (target == null) {
                check.error("alternativeOf names \"" + alternativeOf + "\", which this mod doesn't declare");
            } else if (!target.alternativeOf()
                .trim()
                .isEmpty()) {
                    check.error(
                        "alternativeOf must name a primary dep, and \"" + alternativeOf
                            + "\" is an alternative itself");
                }
        }

        CurseForgeFile curseforge = dep.curseforge();
        String slug = curseforge.slug()
            .trim();
        boolean hasCurseForge = curseforge.project() != 0 || curseforge.file() != 0 || !slug.isEmpty();
        if (hasCurseForge && (curseforge.project() <= 0 || curseforge.file() <= 0)) {
            check.error("curseforge needs both the project and the file id");
        }
        ModrinthFile modrinth = dep.modrinth();
        String modrinthProject = modrinth.project()
            .trim();
        String modrinthVersion = modrinth.version()
            .trim();
        boolean hasModrinth = !modrinthProject.isEmpty() || !modrinthVersion.isEmpty();
        if (hasModrinth && (modrinthProject.isEmpty() || modrinthVersion.isEmpty())) {
            check.error("modrinth needs both the project and the version");
        }

        List<String> urls = new ArrayList<>();
        for (String url : dep.urls()) {
            String trimmed = url.trim();
            if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
                check.error("url \"" + url + "\" must start with https:// or http://");
            }
            urls.add(trimmed);
        }

        List<String> include = paths(check, "include", dep.include());
        List<String> exclude = paths(check, "exclude", dep.exclude());
        List<Object> remap = new ArrayList<>();
        for (String rule : dep.remap()) {
            int arrow = rule.indexOf("->");
            if (arrow < 0 || rule.indexOf("->", arrow + 2) >= 0) {
                check.error("remap \"" + rule + "\" must look like \"from/ -> to/\"");
                continue;
            }
            String from = rule.substring(0, arrow)
                .trim();
            String to = rule.substring(arrow + 2)
                .trim();
            check.path("remap", from);
            check.path("remap", to);
            if (from.endsWith("/") != to.endsWith("/")) {
                check.error(
                    "remap \"" + rule + "\" must map a folder to a folder (both ending in /) or a file to a file");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("from", from);
            entry.put("to", to);
            remap.add(entry);
        }

        String file = dep.file()
            .trim();
        // Alternatives need no source: players get the primary's link and the full build downloads the primary
        boolean primary = alternativeOf.isEmpty();
        if (primary && !hasCurseForge && !hasModrinth && urls.isEmpty()) {
            check.warning("has no CurseForge, Modrinth or URL source: players get no download link");
        } else if (primary && sha1.isEmpty()) {
            check.warning("has no sha1: the full build won't download it");
        }
        if (hasCurseForge && file.isEmpty()) {
            check.warning("has a CurseForge file but no file name: the full build can't use the CurseForge CDN");
        }
        if (hasCurseForge && slug.isEmpty()) {
            check.warning("curseforge has no slug: players get the project page instead of the file page");
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", dep.id());
        json.put("name", dep.name());
        json.put("file", file);
        json.put("sha1", sha1);
        json.put("modId", modId);
        json.put("versions", versions);
        json.put("alternativeOf", alternativeOf);
        if (hasCurseForge) {
            Map<String, Object> cf = new LinkedHashMap<>();
            cf.put("project", curseforge.project());
            cf.put("slug", slug);
            cf.put("file", curseforge.file());
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
        json.put("neutralize", dep.neutralize());
        json.put(
            "side",
            dep.side()
                .name());
        json.put("optional", dep.optional());
        return json;
    }

    private static List<String> paths(Checker check, String field, String[] values) {
        List<String> paths = new ArrayList<>();
        for (String value : values) {
            String trimmed = value.trim();
            check.path(field, trimmed);
            paths.add(trimmed);
        }
        return paths;
    }

    private void write() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", SCHEMA);
        root.put("mods", new ArrayList<Object>(mods.values()));
        try {
            FileObject file = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", OUTPUT, origins.toArray(new Element[0]));
            try (Writer writer = new OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(Json.write(root));
            }
        } catch (IOException e) {
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, "Dep Ensurer: can't write " + OUTPUT + ": " + e.getMessage());
        }
    }

    private void error(Element element, String message) {
        failed = true;
        processingEnv.getMessager()
            .printMessage(Diagnostic.Kind.ERROR, "Dep Ensurer: " + message, element);
    }

    private final class Checker {

        private final TypeElement type;
        private final String prefix;

        Checker(TypeElement type, String id) {
            this.type = type;
            this.prefix = "@EnsureDep(id = \"" + id + "\") on " + type.getSimpleName() + " ";
        }

        void error(String message) {
            EnsureDepProcessor.this.error(type, prefix + message);
        }

        void warning(String message) {
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.WARNING, "Dep Ensurer: " + prefix + message, type);
        }

        void path(String field, String path) {
            if (path.isEmpty()) {
                error(field + " has an empty entry");
            } else if (path.startsWith("/") || path.contains("\\")) {
                error(field + " \"" + path + "\" must be a jar path like assets/x/, without a leading / or \\");
            }
        }
    }
}
