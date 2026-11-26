package industries.angelic.depensurer.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import industries.angelic.depensurer.util.Glob;

public class JarInfoTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private JarInfo read(TestJars jar) throws Exception {
        return JarInfo.read(
            jar.write(new File(temp.getRoot(), "t" + System.nanoTime() + ".jar")),
            Collections.<String>emptyList(),
            Collections.<String, List<Glob>>emptyMap());
    }

    @Test
    public void readsModIdsFromEveryFormat() throws Exception {
        assertEquals(Collections.singletonMap("aether", "1.6"), read(new TestJars().mcmod("aether", "1.6")).mods);
        assertEquals(
            Collections.singletonMap("b", "2"),
            read(
                new TestJars().entry(
                    "mcmod.info",
                    "{\"modListVersion\": 2, \"modList\": [{\"modid\": \"b\", \"version\": \"2\"}]}")).mods);
        assertEquals(Collections.emptyMap(), read(new TestJars().entry("mcmod.info", "{{ not json")).mods);

        Map<String, String> toml = read(
            new TestJars().attribute("Implementation-Version", "1.20.1-1.0")
                .entry(
                    "META-INF/mods.toml",
                    "modLoader=\"javafml\"\n[[mods]]\nmodId=\"aether\"\nversion=\"${file.jarVersion}\"\n"
                        + "[[dependencies.aether]]\nmodId=\"forge\"\n[[mods]]\n  modId = \"aether_extra\"\n  version = \"2\"\n")).mods;
        Map<String, String> expected = new HashMap<>();
        expected.put("aether", "1.20.1-1.0");
        expected.put("aether_extra", "2");
        assertEquals(expected, toml);

        assertEquals(
            Collections.singletonMap("neo", "3"),
            read(new TestJars().entry("META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"neo\"\nversion=\"3\"\n")).mods);
        assertEquals(
            Collections.singletonMap("fab", "4"),
            read(new TestJars().entry("fabric.mod.json", "{\"id\": \"fab\", \"version\": \"4\"}")).mods);
        assertEquals(
            Collections.singletonMap("qu", "5"),
            read(
                new TestJars()
                    .entry("quilt.mod.json", "{\"quilt_loader\": {\"id\": \"qu\", \"version\": \"5\"}}")).mods);
    }

    @Test
    public void seesEarlyHooks() throws Exception {
        assertFalse(read(new TestJars().mcmod("a", "1")).earlyHooks);
        assertTrue(read(new TestJars().attribute("FMLCorePlugin", "a.Core")).earlyHooks);
        assertTrue(read(new TestJars().attribute("TweakClass", "a.Tweak")).earlyHooks);
        assertTrue(read(new TestJars().attribute("FMLAT", "a_at.cfg")).earlyHooks);
    }

    @Test
    public void reportsRootAndGlobHits() throws Exception {
        File file = new TestJars().mcmod("aether", "1")
            .entry("assets/aether/textures/a.png")
            .entry("assets/other/b.png")
            .write(new File(temp.getRoot(), "hits.jar"));
        Map<String, List<Glob>> globs = new HashMap<>();
        globs.put("aether", Arrays.asList(new Glob("assets/aether/**"), new Glob("assets/aether/sounds/**")));
        globs.put("unrelated", Collections.singletonList(new Glob("assets/other/**")));
        JarInfo info = JarInfo.read(file, Arrays.asList("assets/aether/", "assets/missing/"), globs);
        assertEquals(Collections.singleton("assets/aether/"), info.rootHits);
        // Globs are only checked for mod ids the jar has
        assertEquals(Collections.singleton("assets/aether/**"), info.globHits);
    }
}
