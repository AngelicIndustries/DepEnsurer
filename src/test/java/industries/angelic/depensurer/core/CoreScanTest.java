package industries.angelic.depensurer.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CoreScanTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File mods;
    private File store;

    @Before
    public void setUp() {
        mods = new File(temp.getRoot(), "mods");
        store = new File(temp.getRoot(), "depensurer/jars");
        mods.mkdirs();
    }

    private static String dep(String id, String fields) {
        return "{\"id\": \"" + id + "\"" + (fields.isEmpty() ? "" : ", " + fields) + "}";
    }

    private static String sha1Of(File file) throws IOException {
        return "\"sha1\": [\"" + CoreScan.sha1(file) + "\"]";
    }

    private File target(String name) throws IOException {
        return new TestJars().mcmod("aether", "1.6")
            .entry("assets/aether/textures/blocks/holystone.png")
            .entry("com/gildedgames/Aether.class")
            .write(new File(mods, name));
    }

    private void declare(String... deps) throws IOException {
        new TestJars().mcmod("departure", "1")
            .declares("departure", deps)
            .write(new File(mods, "departure.jar"));
    }

    private CoreScan scan(File self) {
        return new CoreScan(mods, Collections.singletonList(store), self, null);
    }

    private Resolution run() {
        return scan(null).run();
    }

    @Test
    public void disablesAnExactMatchAndServesIt() throws Exception {
        File target = target("aether-1.7.10-1.6.jar");
        declare(dep("legacy", sha1Of(target) + ", \"file\": \"aether-1.7.10-1.6.jar\""));

        Resolution r = run();
        File disabled = new File(mods, "aether-1.7.10-1.6.jar.disabled");
        assertFalse(target.exists());
        assertTrue(disabled.exists());
        assertTrue(new File(mods, "departure.jar").exists());
        assertEquals(Collections.singletonList("aether-1.7.10-1.6.jar"), r.disabled);
        assertEquals(1, r.resolved.size());
        Resolution.Resolved resolved = r.find("departure", "legacy");
        assertEquals(disabled.getCanonicalFile(), resolved.file);
        assertTrue(resolved.exact);
        assertTrue(r.missing.isEmpty());
        assertTrue(r.problems.isEmpty());
    }

    @Test
    public void usesAJarDisabledOnAnEarlierLaunch() throws Exception {
        File target = target("aether.jar.disabled");
        declare(dep("legacy", sha1Of(target)));

        Resolution r = run();
        assertTrue(target.exists());
        assertTrue(r.disabled.isEmpty());
        assertEquals(target.getCanonicalFile(), r.find("departure", "legacy").file);
    }

    @Test
    public void shareModeLeavesTheJarLoading() throws Exception {
        File target = target("aether.jar");
        declare(dep("legacy", sha1Of(target) + ", \"neutralize\": false"));

        Resolution r = run();
        assertTrue(target.exists());
        assertTrue(r.disabled.isEmpty());
        assertEquals(target.getCanonicalFile(), r.find("departure", "legacy").file);
    }

    @Test
    public void neutralizeWinsWhenModsDisagree() throws Exception {
        File target = target("aether.jar");
        declare(dep("legacy", sha1Of(target) + ", \"neutralize\": false"));
        new TestJars().declares("other", dep("same", sha1Of(target)))
            .write(new File(mods, "other.jar"));

        Resolution r = run();
        assertFalse(target.exists());
        assertEquals(2, r.resolved.size());
    }

    @Test
    public void matchesLooselyByModIdVersionAndContents() throws Exception {
        File target = target("renamed-by-the-player.jar");
        declare(
            dep("legacy", "\"modId\": \"aether\", \"versions\": \"[1.5,2.0)\", \"include\": [\"assets/aether/**\"]"));

        Resolution r = run();
        assertFalse(target.exists());
        Resolution.Resolved resolved = r.find("departure", "legacy");
        assertFalse(resolved.exact);
        assertEquals(new File(mods, "renamed-by-the-player.jar.disabled").getCanonicalFile(), resolved.file);
    }

    @Test
    public void disablesAndReportsTheWrongVersion() throws Exception {
        File newer = new TestJars().mcmod("aether", "2.1")
            .entry("assets/aether/a.png")
            .write(new File(mods, "aether-2.1.jar"));
        File empty = new TestJars().mcmod("aether", "1.6")
            .write(new File(mods, "aether-1.6-noassets.jar"));
        declare(
            dep("legacy", "\"modId\": \"aether\", \"versions\": \"[1.5,2.0)\", \"include\": [\"assets/aether/**\"]"));

        Resolution r = run();
        assertFalse(newer.exists());
        assertFalse(empty.exists());
        assertTrue(r.resolved.isEmpty());
        assertEquals(1, r.missing.size());
        assertEquals(2, r.missing.get(0).wrongVersions.size());
    }

    @Test
    public void anAlternativeStandsIn() throws Exception {
        target("aether.jar");
        declare(
            dep("aether2", "\"sha1\": [\"0123456789abcdef0123456789abcdef01234567\"], \"file\": \"aether_ii.jar\""),
            dep("aether2_legacy", "\"alternativeOf\": \"aether2\", \"modId\": \"aether\""));

        Resolution r = run();
        Resolution.Resolved resolved = r.find("departure", "aether2");
        assertNotNull(resolved);
        assertEquals("aether2", resolved.primary.id);
        assertEquals("aether2_legacy", resolved.chosen.id);
        assertTrue(r.missing.isEmpty());
    }

    @Test
    public void usesJarsInTheStoreWhereTheyAre() throws Exception {
        File target = new TestJars().entry("assets/aether/a.png")
            .write(new File(store, "aether_ii.jar"));
        declare(dep("aether2", sha1Of(target) + ", \"file\": \"aether_ii.jar\""));

        Resolution r = run();
        assertTrue(target.exists());
        assertTrue(r.disabled.isEmpty());
        assertEquals(target.getCanonicalFile(), r.find("departure", "aether2").file);
    }

    @Test
    public void leavesUndeclaredJarsAlone() throws Exception {
        File enabled = target("aether.jar");
        File disabled = new TestJars().mcmod("old", "1")
            .write(new File(mods, "old.jar.disabled"));
        declare(dep("other", "\"sha1\": [\"0123456789abcdef0123456789abcdef01234567\"], \"file\": \"other.jar\""));

        Resolution r = run();
        assertTrue(enabled.exists());
        assertTrue(disabled.exists());
        assertTrue(r.disabled.isEmpty());
        assertEquals(1, r.missing.size());
    }

    @Test
    public void neverDisablesADeclaringMod() throws Exception {
        new TestJars().mcmod("aether", "1.6")
            .declares("aether", dep("self", "\"modId\": \"aether\""))
            .write(new File(mods, "aether-fork.jar"));

        Resolution r = run();
        assertTrue(new File(mods, "aether-fork.jar").exists());
        assertTrue(r.disabled.isEmpty());
    }

    @Test
    public void reportsAJarWithEarlyHooksThatForgeSawFirst() throws Exception {
        File self = new TestJars().write(new File(mods, "zz-depensurer.jar"));
        File coremod = new TestJars().mcmod("aether", "1.6")
            .attribute("FMLCorePlugin", "a.Core")
            .entry("assets/aether/a.png")
            .write(new File(mods, "aether-core.jar"));
        declare(dep("legacy", "\"modId\": \"aether\""));

        Resolution r = scan(self).run();
        assertFalse(coremod.exists());
        assertTrue(r.restartNeeded());
        assertEquals(Resolution.Problem.Kind.LATE, r.problems.get(0).kind);
        assertTrue(
            r.problems.get(0).message,
            r.problems.get(0).message.startsWith("Forge loaded aether-core.jar before"));
    }

    @Test
    public void lateJarsWithoutEarlyHooksAreFine() throws Exception {
        File self = new TestJars().write(new File(mods, "zz-depensurer.jar"));
        target("aether.jar");
        declare(dep("legacy", "\"modId\": \"aether\""));

        Resolution r = scan(self).run();
        assertFalse(r.restartNeeded());
        assertTrue(r.problems.isEmpty());
        assertEquals(1, r.disabled.size());
    }

    @Test
    public void thePlusPrefixComesFirst() throws Exception {
        File self = new TestJars().write(new File(mods, "+depensurer.jar"));
        new TestJars().mcmod("aether", "1.6")
            .attribute("FMLCorePlugin", "a.Core")
            .write(new File(mods, "1-aether-core.jar"));
        declare(dep("legacy", "\"modId\": \"aether\""));

        Resolution r = scan(self).run();
        assertFalse(r.restartNeeded());
        assertEquals(1, r.disabled.size());
    }

    @Test
    public void reportsABrokenDeclaration() throws Exception {
        new TestJars().entry("META-INF/depensurer.json", "{\"schema\": 9}")
            .write(new File(mods, "broken.jar"));

        Resolution r = run();
        assertEquals(1, r.problems.size());
        assertEquals(Resolution.Problem.Kind.BROKEN_DECLARATION, r.problems.get(0).kind);
        assertTrue(
            r.problems.get(0).message,
            r.problems.get(0).message.startsWith("mods" + File.separator + "broken.jar: "));
    }

    @Test
    public void doesNothingWithoutDeclarations() throws Exception {
        File target = target("aether.jar");

        CoreScan scan = scan(null);
        Resolution r = scan.run();
        assertTrue(target.exists());
        assertTrue(r.resolved.isEmpty());
        assertEquals(0, scan.hashed);
    }

    @Test
    public void hashesOnlyJarsThatCouldMatch() throws Exception {
        File target = target("aether-1.7.10-1.6.jar");
        new TestJars().mcmod("unrelated", "1")
            .entry("assets/unrelated/a.png")
            .write(new File(mods, "unrelated.jar"));
        new TestJars().mcmod("another", "1")
            .write(new File(mods, "1.7.10/another.jar"));
        declare(dep("legacy", sha1Of(target) + ", \"file\": \"aether-1.7.10-1.6.jar\""));

        CoreScan scan = scan(null);
        scan.run();
        assertEquals(1, scan.hashed);
    }

    @Test
    public void findsDeclarationsOnTheClassPath() throws Exception {
        File target = target("aether.jar");
        File classes = temp.newFolder("classes");
        File json = new File(classes, "META-INF/depensurer.json");
        json.getParentFile()
            .mkdirs();
        Files.write(
            json.toPath(),
            ("{\"schema\": 1, \"mods\": [{\"modId\": \"dev\", \"class\": \"dev.Dev\", \"deps\": ["
                + dep("legacy", sha1Of(target))
                + "]}]}").getBytes(StandardCharsets.UTF_8));

        try (URLClassLoader loader = new URLClassLoader(
            new URL[] { classes.toURI()
                .toURL() },
            null)) {
            Resolution r = new CoreScan(mods, Collections.singletonList(store), classes, loader).run();
            assertNotNull(r.find("dev", "legacy"));
            assertFalse(target.exists());
        }
    }

    @Test
    public void replacesAnIdenticalDisabledCopy() throws Exception {
        File target = target("aether.jar");
        File earlier = new File(mods, "aether.jar.disabled");
        Files.copy(target.toPath(), earlier.toPath());
        declare(dep("legacy", sha1Of(target) + ", \"file\": \"aether.jar\""));

        Resolution r = run();
        assertFalse(target.exists());
        assertTrue(earlier.exists());
        assertEquals(1, mods.listFiles((dir, name) -> name.endsWith(".disabled")).length);
        assertEquals(Collections.singletonList("aether.jar"), r.disabled);
    }

    @Test
    public void picksAFreeNameWhenADifferentDisabledFileHasTheName() throws Exception {
        File target = target("aether.jar");
        File earlier = new TestJars().mcmod("aether", "1.5")
            .write(new File(mods, "aether.jar.disabled"));
        declare(dep("legacy", sha1Of(target) + ", \"file\": \"aether.jar\""));

        Resolution r = run();
        assertTrue(earlier.exists());
        assertTrue(new File(mods, "aether (2).jar.disabled").exists());
        assertEquals(Collections.singletonList("aether.jar"), r.disabled);
    }

    @Test
    public void listsMissingOptionalDeps() throws Exception {
        declare(dep("extra", "\"modId\": \"nothere\", \"optional\": true"));

        Resolution r = run();
        assertEquals(1, r.missing.size());
        assertTrue(r.missing.get(0).primary.optional);
        assertNull(r.find("departure", "extra"));
    }
}
