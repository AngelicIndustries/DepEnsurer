package industries.angelic.depensurer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlobTest {

    @Test
    public void matchesSegmentsAndFolders() {
        Glob all = new Glob("assets/aether/**");
        assertTrue(all.matches("assets/aether/textures/blocks/holystone.png"));
        assertTrue(all.matches("assets/aether/sounds.json"));
        assertFalse(all.matches("assets/aetherii/sounds.json"));
        assertFalse(all.matches("assets/aether"));

        Glob oneLevel = new Glob("assets/*/lang/*.lang");
        assertTrue(oneLevel.matches("assets/aether/lang/en_US.lang"));
        assertFalse(oneLevel.matches("assets/aether/lang/old/en_US.lang"));

        Glob anyDepth = new Glob("assets/**/*.ogg");
        assertTrue(anyDepth.matches("assets/a.ogg"));
        assertTrue(anyDepth.matches("assets/aether/sounds/music/a.ogg"));

        Glob single = new Glob("assets/x/a?.png");
        assertTrue(single.matches("assets/x/ab.png"));
        assertFalse(single.matches("assets/x/a/.png"));

        Glob literal = new Glob("assets/x/a+b(1).png");
        assertTrue(literal.matches("assets/x/a+b(1).png"));
        assertFalse(literal.matches("assets/x/aab1.png"));
    }

    @Test
    public void findsTheRoot() {
        assertEquals("assets/aether/", new Glob("assets/aether/**").root());
        assertEquals("assets/", new Glob("assets/**").root());
        assertEquals("assets/x/", new Glob("assets/x/a?.png").root());
        assertEquals("", new Glob("**").root());
        assertEquals("assets/x/a.png", new Glob("assets/x/a.png").root());

        assertTrue(new Glob("assets/aether/**").isSpecific());
        assertTrue(new Glob("assets/x/a.png").isSpecific());
        assertFalse(new Glob("assets/**").isSpecific());
        assertFalse(new Glob("assets/*/lang/**").isSpecific());
        assertFalse(new Glob("pack.png").isSpecific());
    }
}
