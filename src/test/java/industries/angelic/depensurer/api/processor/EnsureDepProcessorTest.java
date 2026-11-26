package industries.angelic.depensurer.api.processor;

import static industries.angelic.depensurer.api.processor.Compilation.lines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.declaration.DeclarationReader;
import industries.angelic.depensurer.declaration.DepDeclaration;
import industries.angelic.depensurer.declaration.ModDeclaration;

public class EnsureDepProcessorTest {

    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";

    /** A mod class with the given annotation lines between {@code @Mod} and the class. */
    private static String mod(String className, String... annotations) {
        StringBuilder source = new StringBuilder(
            lines(
                "package test;",
                "import cpw.mods.fml.common.Mod;",
                "import industries.angelic.depensurer.api.*;",
                "@Mod(modid = \"" + className.toLowerCase() + "\", name = \"" + className + "\", version = \"1\")"));
        for (String annotation : annotations) {
            source.append(annotation)
                .append('\n');
        }
        return source.append("public class ")
            .append(className)
            .append(" {}\n")
            .toString();
    }

    private static List<ModDeclaration> read(Compilation compilation) throws Exception {
        return DeclarationReader.read(new StringReader(compilation.json), "test");
    }

    @Test
    public void writesEveryField() throws Exception {
        Compilation c = Compilation.of(
            mod(
                "Aether",
                "@EnsureDep(id = \"aether2\", name = \"Aether II\", file = \"aether_ii.jar\",",
                "    sha1 = \"" + SHA1.toUpperCase() + "\", modId = \"aether\", versions = \"[0.3,0.4)\",",
                "    curseforge = @CurseForgeFile(project = 255308, slug = \"aether-ii\", file = 2960610),",
                "    modrinth = @ModrinthFile(project = \"JD2NSu5O\", version = \"cZNxPTqa\"),",
                "    urls = \"https://example.com/aether_ii.jar\",",
                "    include = { \"assets/aether/**\", \"assets/aether/sounds.json\" }, exclude = \"assets/aether/lang/**\",",
                "    remap = \"assets/aether/ -> assets/aetherii/\", side = EnsureDep.Side.BOTH)",
                "@EnsureDep(id = \"aether2_legacy\", alternativeOf = \"aether2\", modId = \"aether\",",
                "    neutralize = false, optional = true, modrinth = @ModrinthFile(project = \"p\", version = \"v\"),",
                "    sha1 = \"" + SHA1 + "\")"));
        assertTrue(c.errorsAndWarnings(), c.success);
        assertEquals(c.errorsAndWarnings(), Collections.emptyList(), c.warnings);

        List<ModDeclaration> mods = read(c);
        assertEquals(1, mods.size());
        ModDeclaration mod = mods.get(0);
        assertEquals("aether", mod.modId);
        assertEquals("test.Aether", mod.className);
        assertEquals(2, mod.deps.size());

        DepDeclaration dep = mod.dep("aether2");
        assertEquals("Aether II", dep.name);
        assertEquals("aether_ii.jar", dep.file);
        assertEquals(Collections.singletonList(SHA1), dep.sha1);
        assertEquals("aether", dep.modId);
        assertEquals("[0.3,0.4)", dep.versions);
        assertEquals("", dep.alternativeOf);
        assertEquals(255308, dep.curseforge.project);
        assertEquals("aether-ii", dep.curseforge.slug);
        assertEquals(2960610, dep.curseforge.file);
        assertEquals("JD2NSu5O", dep.modrinth.project);
        assertEquals("cZNxPTqa", dep.modrinth.version);
        assertEquals(Collections.singletonList("https://example.com/aether_ii.jar"), dep.urls);
        assertEquals(Arrays.asList("assets/aether/**", "assets/aether/sounds.json"), dep.include);
        assertEquals(Collections.singletonList("assets/aether/lang/**"), dep.exclude);
        assertEquals(1, dep.remap.size());
        assertEquals("assets/aether/", dep.remap.get(0).from);
        assertEquals("assets/aetherii/", dep.remap.get(0).to);
        assertTrue(dep.neutralize);
        assertEquals(EnsureDep.Side.BOTH, dep.side);
        assertFalse(dep.optional);

        DepDeclaration alternative = mod.dep("aether2_legacy");
        assertEquals("aether2", alternative.alternativeOf);
        assertTrue(alternative.isAlternative());
        assertNull(alternative.curseforge);
        assertEquals(Collections.singletonList("assets/**"), alternative.include);
        assertFalse(alternative.neutralize);
        assertTrue(alternative.optional);
        assertEquals(EnsureDep.Side.CLIENT, alternative.side);
    }

    @Test
    public void readsTheModIdFromAConstant() throws Exception {
        Compilation c = Compilation.of(
            lines(
                "package test;",
                "import cpw.mods.fml.common.Mod;",
                "import industries.angelic.depensurer.api.*;",
                "@Mod(modid = Constant.MODID, version = \"1\")",
                "@EnsureDep(id = \"a\", sha1 = \"" + SHA1
                    + "\", modrinth = @ModrinthFile(project = \"p\", version = \"v\"))",
                "public class Constant { public static final String MODID = \"constantmod\"; }"));
        assertTrue(c.errorsAndWarnings(), c.success);
        assertEquals("constantmod", read(c).get(0).modId);
    }

    @Test
    public void writesOneEntryPerModSortedByClass() throws Exception {
        String dep = "@EnsureDep(id = \"a\", sha1 = \"" + SHA1
            + "\", modrinth = @ModrinthFile(project = \"p\", version = \"v\"))";
        Compilation c = Compilation.of(mod("Zeta", dep), mod("Alpha", dep));
        assertTrue(c.errorsAndWarnings(), c.success);
        List<ModDeclaration> mods = read(c);
        assertEquals(2, mods.size());
        assertEquals("test.Alpha", mods.get(0).className);
        assertEquals("test.Zeta", mods.get(1).className);
    }

    @Test
    public void writesNothingWithoutAnnotations() throws Exception {
        Compilation c = Compilation.of(lines("package test;", "public class Plain {}"));
        assertTrue(c.errorsAndWarnings(), c.success);
        assertNull(c.json);
    }

    @Test
    public void needsTheModAnnotation() throws Exception {
        Compilation c = Compilation.of(
            lines(
                "package test;",
                "import industries.angelic.depensurer.api.*;",
                "@EnsureDep(id = \"a\", sha1 = \"" + SHA1 + "\")",
                "public class NotAMod {}"));
        assertFalse(c.success);
        assertTrue(c.errorsAndWarnings(), c.hasError("has no @Mod"));
        assertNull(c.json);
    }

    @Test
    public void rejectsBadIds() throws Exception {
        Compilation c = Compilation.of(
            mod(
                "Ids",
                "@EnsureDep(id = \"a\", sha1 = \"" + SHA1 + "\")",
                "@EnsureDep(id = \"a\", sha1 = \"" + SHA1 + "\")",
                "@EnsureDep(id = \"has space\", sha1 = \"" + SHA1 + "\")"));
        assertFalse(c.success);
        assertTrue(c.errorsAndWarnings(), c.hasError("have the id \"a\""));
        assertTrue(c.errorsAndWarnings(), c.hasError("id must be letters"));
    }

    @Test
    public void needsSomethingToMatch() throws Exception {
        Compilation c = Compilation.of(
            mod(
                "Matching",
                "@EnsureDep(id = \"nothing\")",
                "@EnsureDep(id = \"short\", sha1 = \"abc\")",
                "@EnsureDep(id = \"bare\", modId = \"x\", versions = \"1.0\")",
                "@EnsureDep(id = \"orphan\", sha1 = \"" + SHA1 + "\", versions = \"[1.0]\")"));
        assertFalse(c.success);
        assertTrue(c.errorsAndWarnings(), c.hasError("\"nothing\") on Matching needs sha1 or modId"));
        assertTrue(c.errorsAndWarnings(), c.hasError("sha1 \"abc\" is not 40 hex digits"));
        assertTrue(c.errorsAndWarnings(), c.hasError("versions \"1.0\" must be a bracketed range"));
        assertTrue(c.errorsAndWarnings(), c.hasError("\"orphan\") on Matching versions needs modId"));
    }

    @Test
    public void checksAlternatives() throws Exception {
        Compilation c = Compilation.of(
            mod(
                "Alternatives",
                "@EnsureDep(id = \"main\", sha1 = \"" + SHA1 + "\")",
                "@EnsureDep(id = \"alt\", alternativeOf = \"main\", sha1 = \"" + SHA1 + "\")",
                "@EnsureDep(id = \"chain\", alternativeOf = \"alt\", sha1 = \"" + SHA1 + "\")",
                "@EnsureDep(id = \"self\", alternativeOf = \"self\", sha1 = \"" + SHA1 + "\")",
                "@EnsureDep(id = \"lost\", alternativeOf = \"missing\", sha1 = \"" + SHA1 + "\")"));
        assertFalse(c.success);
        assertTrue(c.errorsAndWarnings(), c.hasError("\"alt\" is an alternative itself"));
        assertTrue(c.errorsAndWarnings(), c.hasError("can't name the dep itself"));
        assertTrue(c.errorsAndWarnings(), c.hasError("names \"missing\", which this mod doesn't declare"));
        assertFalse(c.errorsAndWarnings(), c.hasError("(id = \"alt\")"));
    }

    @Test
    public void needsCompleteSources() throws Exception {
        Compilation c = Compilation.of(
            mod(
                "Sources",
                "@EnsureDep(id = \"cf\", sha1 = \"" + SHA1 + "\", curseforge = @CurseForgeFile(project = 1))",
                "@EnsureDep(id = \"mr\", sha1 = \"" + SHA1 + "\", modrinth = @ModrinthFile(version = \"v\"))",
                "@EnsureDep(id = \"url\", sha1 = \"" + SHA1 + "\", urls = \"ftp://example.com/x.jar\")"));
        assertFalse(c.success);
        assertTrue(c.errorsAndWarnings(), c.hasError("curseforge needs both the project and the file id"));
        assertTrue(c.errorsAndWarnings(), c.hasError("modrinth needs both the project and the version"));
        assertTrue(c.errorsAndWarnings(), c.hasError("must start with https:// or http://"));
    }

    @Test
    public void checksPathsAndRemaps() throws Exception {
        Compilation c = Compilation.of(
            mod(
                "Paths",
                "@EnsureDep(id = \"a\", sha1 = \"" + SHA1
                    + "\", include = \"/assets/x/**\", exclude = \"assets\\\\x\",",
                "    remap = { \"assets/x/\", \"assets/x/ -> assets/y/a.png\", \"assets/x/ -> \" })"));
        assertFalse(c.success);
        assertTrue(c.errorsAndWarnings(), c.hasError("include \"/assets/x/**\" must be a jar path"));
        assertTrue(c.errorsAndWarnings(), c.hasError("exclude \"assets\\x\" must be a jar path"));
        assertTrue(c.errorsAndWarnings(), c.hasError("remap \"assets/x/\" must look like"));
        assertTrue(c.errorsAndWarnings(), c.hasError("must map a folder to a folder"));
        assertTrue(c.errorsAndWarnings(), c.hasError("remap has an empty entry"));
    }

    @Test
    public void warnsWhenPlayersOrTheFullBuildCantGetTheFile() throws Exception {
        Compilation c = Compilation.of(
            mod(
                "Warnings",
                "@EnsureDep(id = \"local\", modId = \"x\")",
                "@EnsureDep(id = \"unverified\", modId = \"x\", modrinth = @ModrinthFile(project = \"p\", version = \"v\"))",
                "@EnsureDep(id = \"cdn\", sha1 = \"" + SHA1
                    + "\", curseforge = @CurseForgeFile(project = 1, file = 2))",
                "@EnsureDep(id = \"quiet\", alternativeOf = \"cdn\", modId = \"y\")"));
        assertTrue(c.errorsAndWarnings(), c.success);
        assertTrue(
            c.errorsAndWarnings(),
            c.hasWarning("\"local\") on Warnings has no CurseForge, Modrinth or URL source"));
        assertTrue(c.errorsAndWarnings(), c.hasWarning("\"unverified\") on Warnings has no sha1"));
        assertTrue(c.errorsAndWarnings(), c.hasWarning("\"cdn\") on Warnings has a CurseForge file but no file name"));
        assertTrue(c.errorsAndWarnings(), c.hasWarning("\"cdn\") on Warnings curseforge has no slug"));
        assertEquals(c.errorsAndWarnings(), 4, c.warnings.size());
    }
}
