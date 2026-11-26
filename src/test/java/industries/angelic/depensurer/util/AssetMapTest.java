package industries.angelic.depensurer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import industries.angelic.depensurer.declaration.DepDeclaration;

public class AssetMapTest {

    private static final List<String> ENTRIES = Arrays.asList(
        "META-INF/MANIFEST.MF",
        "com/x/Aether.class",
        "assets/aether/",
        "assets/aether/textures/block/holystone.png",
        "assets/aether/lang/en_us.json",
        "assets/aether/sounds.json",
        "assets/aether_legacy/textures/items/key.png",
        "pack.png");

    private static DepDeclaration.Remap remap(String from, String to) {
        return new DepDeclaration.Remap(from, to);
    }

    @Test
    public void servesIncludedEntriesAsTheyAre() {
        AssetMap map = new AssetMap(
            ENTRIES,
            Collections.singletonList("assets/**"),
            Collections.singletonList("assets/*/lang/**"),
            Collections.<DepDeclaration.Remap>emptyList());
        assertEquals(
            "assets/aether/textures/block/holystone.png",
            map.jarPath("assets/aether/textures/block/holystone.png"));
        assertNull(map.jarPath("assets/aether/lang/en_us.json"));
        assertNull(map.jarPath("com/x/Aether.class"));
        assertNull(map.jarPath("assets/aether/"));
        assertEquals(Arrays.asList("aether", "aether_legacy"), new java.util.ArrayList<>(map.domains()));
        assertEquals(3, map.size());
    }

    @Test
    public void movesRemappedEntriesAndOnlyServesThemThere() {
        AssetMap map = new AssetMap(
            ENTRIES,
            Collections.singletonList("assets/aether/**"),
            Collections.<String>emptyList(),
            Arrays.asList(
                remap("assets/aether/", "assets/aetherii/"),
                remap("assets/aether/textures/block/", "assets/aetherii/textures/blocks/")));
        // The longer, more specific rule wins
        assertEquals(
            "assets/aether/textures/block/holystone.png",
            map.jarPath("assets/aetherii/textures/blocks/holystone.png"));
        assertNull(map.jarPath("assets/aetherii/textures/block/holystone.png"));
        assertEquals("assets/aether/sounds.json", map.jarPath("assets/aetherii/sounds.json"));
        assertNull(map.jarPath("assets/aether/sounds.json"));
        assertEquals(Collections.singleton("aetherii"), map.domains());
    }

    @Test
    public void remapsSingleFilesAndKeepsTheFirstOnACollision() {
        AssetMap map = new AssetMap(
            ENTRIES,
            Arrays.asList("assets/aether_legacy/**", "assets/aether/sounds.json"),
            Collections.<String>emptyList(),
            Arrays.asList(
                remap("assets/aether/sounds.json", "assets/aether_legacy/textures/items/key.png"),
                remap("assets/aether_legacy/textures/items/key.png", "assets/x/key.png")));
        assertEquals("assets/aether_legacy/textures/items/key.png", map.jarPath("assets/x/key.png"));
        assertEquals("assets/aether/sounds.json", map.jarPath("assets/aether_legacy/textures/items/key.png"));
        assertEquals(Collections.<String>emptyList(), map.collisions());
    }

    @Test
    public void reportsCollisions() {
        AssetMap map = new AssetMap(
            Arrays.asList("assets/a/x.png", "assets/b/x.png"),
            Collections.singletonList("assets/**"),
            Collections.<String>emptyList(),
            Collections.singletonList(remap("assets/a/", "assets/b/")));
        assertEquals("assets/a/x.png", map.jarPath("assets/b/x.png"));
        assertEquals(Collections.singletonList("assets/b/x.png -> assets/b/x.png"), map.collisions());
    }
}
