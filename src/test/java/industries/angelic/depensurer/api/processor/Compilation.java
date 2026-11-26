package industries.angelic.depensurer.api.processor;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import cpw.mods.fml.common.Mod;
import industries.angelic.depensurer.api.EnsureDep;

/** Runs {@link EnsureDepProcessor} over in-memory sources with the JDK's compiler. */
final class Compilation {

    private static final Pattern PACKAGE = Pattern.compile("package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS = Pattern.compile("(?:class|interface)\\s+(\\w+)");

    final boolean success;
    final List<String> errors = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    /** Null when the processor wrote nothing. */
    final String json;

    private Compilation(boolean success, DiagnosticCollector<JavaFileObject> diagnostics, String json) {
        this.success = success;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            String message = diagnostic.getMessage(Locale.ROOT);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(message);
            } else if (diagnostic.getKind() == Diagnostic.Kind.WARNING
                || diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING) {
                    warnings.add(message);
                }
        }
        this.json = json;
    }

    static Compilation of(String... sources) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull("These tests need a JDK", javac);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        File out = Files.createTempDirectory("depensurer-processor")
            .toFile();
        StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(out));
        files.setLocation(
            StandardLocation.CLASS_PATH,
            Arrays.asList(locationOf(Mod.class), locationOf(EnsureDep.class)));

        List<JavaFileObject> units = new ArrayList<>();
        for (String source : sources) {
            units.add(new Source(source));
        }
        JavaCompiler.CompilationTask task = javac
            .getTask(null, files, diagnostics, Collections.singletonList("-proc:only"), null, units);
        task.setProcessors(Collections.singletonList(new EnsureDepProcessor()));
        boolean success = task.call();
        files.close();

        File json = new File(out, EnsureDepProcessor.OUTPUT);
        return new Compilation(
            success,
            diagnostics,
            json.isFile() ? new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8) : null);
    }

    /** Joins lines, so test sources stay readable without text blocks. */
    static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    String errorsAndWarnings() {
        return "errors " + errors + ", warnings " + warnings;
    }

    boolean hasError(String fragment) {
        return errors.stream()
            .anyMatch(e -> e.contains(fragment));
    }

    boolean hasWarning(String fragment) {
        return warnings.stream()
            .anyMatch(w -> w.contains(fragment));
    }

    private static File locationOf(Class<?> type) {
        try {
            return new File(
                type.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String code;

        Source(String code) {
            super(uriFor(code), Kind.SOURCE);
            this.code = code;
        }

        private static URI uriFor(String code) {
            Matcher pkg = PACKAGE.matcher(code);
            Matcher cls = CLASS.matcher(code);
            if (!cls.find()) {
                throw new IllegalArgumentException("No class in test source");
            }
            String path = (pkg.find() ? pkg.group(1)
                .replace('.', '/') + "/" : "") + cls.group(1) + ".java";
            return URI.create("string:///" + path);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
