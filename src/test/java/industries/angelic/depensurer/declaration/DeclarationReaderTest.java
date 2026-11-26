package industries.angelic.depensurer.declaration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import industries.angelic.depensurer.api.EnsureDep;

public class DeclarationReaderTest {

    private static List<ModDeclaration> read(String json) throws DeclarationException {
        return DeclarationReader.read(new StringReader(json), "mods/test.jar");
    }

    private static void assertRejected(String json, String fragment) {
        try {
            read(json);
            fail("Accepted " + json);
        } catch (DeclarationException e) {
            assertTrue(
                e.getMessage(),
                e.getMessage()
                    .contains(fragment));
            assertTrue(
                e.getMessage(),
                e.getMessage()
                    .startsWith("mods/test.jar: "));
        }
    }

    @Test
    public void fillsInTheAnnotationDefaults() throws Exception {
        List<ModDeclaration> mods = read(
            "{\"schema\": 1, \"mods\": [{\"modId\": \"m\", \"class\": \"a.M\", \"deps\": [{\"id\": \"d\", \"sha1\": [\"ABCDEF0123456789ABCDEF0123456789ABCDEF01\"]}]}]}");
        ModDeclaration mod = mods.get(0);
        assertEquals("m", mod.modId);
        assertEquals("a.M", mod.className);
        assertEquals("mods/test.jar", mod.source);
        DepDeclaration dep = mod.dep("d");
        assertEquals(Collections.singletonList("abcdef0123456789abcdef0123456789abcdef01"), dep.sha1);
        assertEquals(Collections.singletonList("assets/**"), dep.include);
        assertEquals(Collections.emptyList(), dep.exclude);
        assertEquals(Collections.emptyList(), dep.remap);
        assertEquals(Collections.emptyList(), dep.urls);
        assertNull(dep.curseforge);
        assertNull(dep.modrinth);
        assertTrue(dep.neutralize);
        assertEquals(EnsureDep.Side.CLIENT, dep.side);
        assertEquals("d", dep.displayName());
    }

    @Test
    public void rejectsOtherSchemas() {
        assertRejected("{\"schema\": 2, \"mods\": []}", "has schema 2, this Dep Ensurer reads 1 to 1");
        assertRejected("{\"mods\": []}", "has schema 0");
    }

    @Test
    public void rejectsBrokenFiles() {
        assertRejected("{\"schema\": 1, \"mods\": [", "is not valid JSON");
        assertRejected("[]", "is not a JSON object");
        assertRejected("{\"schema\": 1, \"mods\": {}}", "has an unexpected shape");
        assertRejected(
            "{\"schema\": 1, \"mods\": [{\"modId\": \"m\", \"deps\": [{\"name\": \"x\"}]}]}",
            "mod m has a dep without an id");
        assertRejected(
            "{\"schema\": 1, \"mods\": [{\"modId\": \"m\", \"deps\": [{\"id\": \"d\", \"side\": \"SERVER\"}]}]}",
            "dep d has an unknown side SERVER");
    }
}
