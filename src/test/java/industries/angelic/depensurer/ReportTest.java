package industries.angelic.depensurer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import industries.angelic.depensurer.core.CoreScan;
import industries.angelic.depensurer.core.Resolution;

public class ReportTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** Runs a real scan over a mods folder whose only jar declares the given deps. */
    private Resolution scan(String... deps) throws Exception {
        File mods = temp.newFolder("mods");
        File declaring = new File(mods, "departure.jar");
        try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(
            new java.io.FileOutputStream(declaring))) {
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/depensurer.json"));
            out.write(
                ("{\"schema\": 1, \"mods\": [{\"modId\": \"departure\", \"class\": \"a.D\", \"deps\": ["
                    + String.join(", ", deps)
                    + "]}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new CoreScan(mods, Collections.<File>emptyList(), null, null).run();
    }

    @Test
    public void listsMissingFilesWithTheirPages() throws Exception {
        Resolution r = scan(
            "{\"id\": \"aether2\", \"name\": \"Aether II\", \"file\": \"aether_ii.jar\", \"modId\": \"aether\","
                + " \"curseforge\": {\"project\": 1, \"slug\": \"aether-ii\", \"file\": 2960610},"
                + " \"modrinth\": {\"project\": \"JD2NSu5O\", \"version\": \"cZNxPTqa\"}, \"urls\": [\"https://mirror/x.jar\"]}",
            "{\"id\": \"noslug\", \"modId\": \"n\", \"curseforge\": {\"project\": 42, \"file\": 7}}");
        Report report = Report.of(r, false, id -> id.equals("departure") ? "Aether Legacy Departure" : id);
        assertFalse(report.isEmpty());
        assertEquals(2, report.missing.size());

        Report.Entry entry = report.missing.get(0);
        assertEquals("Aether II", entry.name);
        assertEquals("aether_ii.jar", entry.file);
        assertEquals("Aether Legacy Departure", entry.neededBy);
        assertEquals(2, entry.links.size());
        assertEquals("https://www.curseforge.com/minecraft/mc-mods/aether-ii/files/2960610", entry.links.get(0).url);
        assertEquals("https://modrinth.com/mod/JD2NSu5O/version/cZNxPTqa", entry.links.get(1).url);
        assertEquals("https://www.curseforge.com/projects/42", report.missing.get(1).links.get(0).url);

        List<String> lines = report.lines();
        assertTrue(
            lines.toString(),
            lines.get(1)
                .contains("Aether II (aether_ii.jar), needed by Aether Legacy Departure"));
        assertFalse(
            lines.toString(),
            lines.toString()
                .contains("mirror"));
    }

    @Test
    public void leavesOutOptionalAndClientOnlyDepsWhereTheyDontMatter() throws Exception {
        Resolution r = scan(
            "{\"id\": \"optional\", \"modId\": \"o\", \"optional\": true}",
            "{\"id\": \"client\", \"modId\": \"c\", \"side\": \"CLIENT\"}",
            "{\"id\": \"both\", \"modId\": \"b\", \"side\": \"BOTH\"}");
        assertEquals(2, Report.of(r, false, id -> id).missing.size());
        Report server = Report.of(r, true, id -> id);
        assertEquals(1, server.missing.size());
        assertEquals("both", server.missing.get(0).name);
    }

    @Test
    public void takesLinksFromAnAlternativeWhenThePrimaryHasNone() throws Exception {
        Resolution r = scan(
            "{\"id\": \"main\", \"modId\": \"m\"}",
            "{\"id\": \"alt\", \"alternativeOf\": \"main\", \"modId\": \"a\", \"modrinth\": {\"project\": \"p\", \"version\": \"v\"}}");
        Report report = Report.of(r, false, id -> id);
        assertEquals(1, report.missing.size());
        assertEquals("https://modrinth.com/mod/p/version/v", report.missing.get(0).links.get(0).url);
    }

    @Test
    public void isEmptyWhenEverythingWasFound() throws Exception {
        Resolution r = scan("{\"id\": \"optional\", \"modId\": \"o\", \"optional\": true}");
        assertTrue(
            Report.of(r, false, id -> id)
                .isEmpty());
    }
}
