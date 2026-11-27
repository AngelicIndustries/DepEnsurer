package industries.angelic.depensurer.api.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.declaration.DeclarationReader;
import industries.angelic.depensurer.declaration.DepDeclaration;
import industries.angelic.depensurer.declaration.ModDeclaration;

public class PropertiesDeclarationTest {

    private static final String SHA1 = "e66d53196fd6eef018be095fd9f190b89d02e0c1";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private static Properties properties(String... keysAndValues) {
        Properties properties = new Properties();
        properties.setProperty("modId", "aether_legacy");
        properties.setProperty("depensurer.version", "0.0.1");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }

    private String generate(Properties properties) {
        return PropertiesDeclaration.generate(properties, errors, warnings);
    }

    @Test
    public void writesWhatTheAnnotationWouldWrite() throws Exception {
        String json = generate(
            properties(
                "depensurer.deps",
                "aether2, legacy",
                "depensurer.aether2.name",
                "The Aether II 1.7.10-1.6",
                "depensurer.aether2.file",
                "aether-1.7.10-1.6.jar",
                "depensurer.aether2.sha1",
                SHA1.toUpperCase(),
                "depensurer.aether2.modId",
                "aether",
                "depensurer.aether2.versions",
                "[1.7.10-1.6],[1.7.10-1.7]",
                "depensurer.aether2.curseforge",
                "223796:aether-ii:2273367",
                "depensurer.aether2.urls",
                "https://example.com/a.jar",
                "depensurer.aether2.include",
                "assets/aether/**",
                "depensurer.aether2.remap",
                "assets/aether/ -> assets/aetherii/",
                "depensurer.legacy.file",
                "aether-1.7.10-v1.1.2.3.jar",
                "depensurer.legacy.sha1",
                "d45466de894c24fb2c7895704509073ea97eaf69, 7358043a6c3c3c2647c4a28342f7c4861897f29f",
                "depensurer.legacy.modrinth",
                "YhmgMVyu:4NuI8eHN",
                "depensurer.legacy.curseforge",
                "255308:5028330",
                "depensurer.legacy.include",
                "assets/aether_legacy/lang/**; assets/aether_legacy/textures/**",
                "depensurer.legacy.neutralize",
                "false",
                "depensurer.legacy.side",
                "both",
                "depensurer.legacy.optional",
                "TRUE"));
        assertEquals(Collections.emptyList(), errors);
        assertEquals(
            Collections.singletonList(
                "depensurer.legacy: curseforge has no slug: players get the project page instead of the file page"),
            warnings);

        List<ModDeclaration> mods = DeclarationReader.read(new StringReader(json), "test");
        assertEquals(1, mods.size());
        ModDeclaration mod = mods.get(0);
        assertEquals("aether_legacy", mod.modId);
        assertEquals("", mod.className);
        assertEquals("aether2", mod.deps.get(0).id);

        DepDeclaration aether2 = mod.dep("aether2");
        assertEquals("The Aether II 1.7.10-1.6", aether2.name);
        assertEquals(Collections.singletonList(SHA1), aether2.sha1);
        assertEquals("[1.7.10-1.6],[1.7.10-1.7]", aether2.versions);
        assertEquals(223796, aether2.curseforge.project);
        assertEquals("aether-ii", aether2.curseforge.slug);
        assertEquals(2273367, aether2.curseforge.file);
        assertEquals(Collections.singletonList("https://example.com/a.jar"), aether2.urls);
        assertEquals("assets/aetherii/", aether2.remap.get(0).to);
        assertTrue(aether2.neutralize);
        assertEquals(EnsureDep.Side.CLIENT, aether2.side);

        DepDeclaration legacy = mod.dep("legacy");
        assertEquals(2, legacy.sha1.size());
        assertEquals("YhmgMVyu", legacy.modrinth.project);
        assertEquals("", legacy.curseforge.slug);
        assertEquals(Arrays.asList("assets/aether_legacy/lang/**", "assets/aether_legacy/textures/**"), legacy.include);
        assertFalse(legacy.neutralize);
        assertEquals(EnsureDep.Side.BOTH, legacy.side);
        assertTrue(legacy.optional);
    }

    @Test
    public void writesNothingWithoutDeps() {
        assertNull(generate(properties()));
        assertEquals(Collections.emptyList(), errors);
    }

    @Test
    public void catchesTypos() {
        assertNull(
            generate(
                properties(
                    "depensurer.deps",
                    "a",
                    "depensurer.a.sha",
                    SHA1,
                    "depensurer.b.sha1",
                    SHA1,
                    "depensurer.typo",
                    "x")));
        assertTrue(
            errors.toString(),
            errors.contains(
                "depensurer.a.sha: sha is not a field; the fields are "
                    + "name, file, sha1, modId, versions, alternativeOf, curseforge, modrinth, urls, include, exclude, remap, "
                    + "neutralize, side, optional"));
        assertTrue(errors.toString(), errors.contains("depensurer.b.* is set but b isn't in depensurer.deps"));
        assertTrue(
            errors.toString(),
            errors.stream()
                .anyMatch(e -> e.startsWith("depensurer.typo is not a Dep Ensurer property")));
    }

    @Test
    public void needsTheDepListAndTheModId() {
        assertNull(generate(properties("depensurer.a.sha1", SHA1)));
        assertEquals(
            Collections
                .singletonList("depensurer.deps is missing; list the ids of the deps, for example depensurer.deps = a"),
            errors);

        errors.clear();
        Properties noModId = properties("depensurer.deps", "a, a", "depensurer.a.sha1", SHA1);
        noModId.remove("modId");
        assertNull(generate(noModId));
        assertTrue(errors.toString(), errors.contains("depensurer.deps lists a twice"));
        assertTrue(
            errors.toString(),
            errors.contains("modId is missing; set it in gradle.properties as the GTNH buildscript expects"));
    }

    @Test
    public void checksValuesLikeTheProcessor() {
        assertNull(
            generate(
                properties(
                    "depensurer.deps",
                    "a",
                    "depensurer.a.sha1",
                    "abc",
                    "depensurer.a.curseforge",
                    "223796",
                    "depensurer.a.modrinth",
                    "onlyproject",
                    "depensurer.a.neutralize",
                    "yes",
                    "depensurer.a.side",
                    "server")));
        assertTrue(errors.toString(), errors.contains("depensurer.a: sha1 \"abc\" is not 40 hex digits"));
        assertTrue(
            errors.toString(),
            errors.contains("depensurer.a.curseforge must be project:slug:file, for example 223796:aether-ii:2273367"));
        assertTrue(
            errors.toString(),
            errors.contains("depensurer.a.modrinth must be project:version, for example YhmgMVyu:4NuI8eHN"));
        assertTrue(errors.toString(), errors.contains("depensurer.a.neutralize must be true or false"));
        assertTrue(errors.toString(), errors.contains("depensurer.a: side must be CLIENT or BOTH"));
    }

    @Test
    public void writesAndRemovesTheFile() throws Exception {
        File props = temp.newFile("declaration.properties");
        File out = temp.newFolder("out");
        File json = new File(out, "META-INF/depensurer.json");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (FileOutputStream file = new FileOutputStream(props)) {
            properties("depensurer.deps", "a", "depensurer.a.modId", "x").store(file, null);
        }
        assertEquals(0, run(props, out, stdout, stderr));
        assertTrue(json.isFile());
        assertTrue(
            stdout.toString("UTF-8"),
            stdout.toString("UTF-8")
                .contains("warning: Dep Ensurer: depensurer.a: has no CurseForge, Modrinth or URL source"));

        try (FileOutputStream file = new FileOutputStream(props)) {
            properties().store(file, null);
        }
        assertEquals(0, run(props, out, stdout, stderr));
        assertFalse(json.exists());

        try (FileOutputStream file = new FileOutputStream(props)) {
            properties("depensurer.deps", "a").store(file, null);
        }
        assertEquals(1, run(props, out, stdout, stderr));
        assertTrue(
            stderr.toString("UTF-8"),
            stderr.toString("UTF-8")
                .contains("error: Dep Ensurer: depensurer.a: needs sha1 or modId"));
    }

    private static int run(File props, File out, ByteArrayOutputStream stdout, ByteArrayOutputStream stderr)
        throws Exception {
        return PropertiesDeclaration.run(
            new String[] { props.getPath(), out.getPath() },
            new PrintStream(stdout, true, StandardCharsets.UTF_8.name()),
            new PrintStream(stderr, true, StandardCharsets.UTF_8.name()));
    }
}
