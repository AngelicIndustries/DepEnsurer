package industries.angelic.depensurer.api.processor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.api.EnsureDeps;

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

        List<DepFields> deps = new ArrayList<>();
        Map<String, DepFields> byId = new LinkedHashMap<>();
        for (EnsureDep annotation : type.getAnnotationsByType(EnsureDep.class)) {
            DepFields dep = DepFields.of(annotation);
            deps.add(dep);
            if (byId.put(dep.id, dep) != null) {
                error(type, "two @EnsureDep on " + className + " have the id \"" + dep.id + "\"");
            }
        }

        List<Object> depList = new ArrayList<>();
        for (DepFields dep : deps) {
            depList.add(toJson(type, dep, byId));
        }
        mods.put(className, DepChecker.mod(modId, className, depList));
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

    private Map<String, Object> toJson(TypeElement type, DepFields dep, Map<String, DepFields> byId) {
        final String prefix = "@EnsureDep(id = \"" + dep.id + "\") on " + type.getSimpleName() + " ";
        return DepChecker.check(dep, byId, new DepChecker.Reporter() {

            @Override
            public void error(String message) {
                EnsureDepProcessor.this.error(type, prefix + message);
            }

            @Override
            public void warning(String message) {
                processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.WARNING, "Dep Ensurer: " + prefix + message, type);
            }
        });
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
}
