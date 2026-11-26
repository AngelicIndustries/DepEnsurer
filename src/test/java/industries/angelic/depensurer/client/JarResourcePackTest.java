package industries.angelic.depensurer.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.minecraft.util.ResourceLocation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.declaration.DepDeclaration;

public class JarResourcePackTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void servesRemappedAssetsFromTheJar() throws Exception {
        File jar = temp.newFile("target.jar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("assets/depensurertarget/textures/items/marker.png"));
            out.write("png".getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new ZipEntry("assets/depensurertarget/lang/en_US.lang"));
            out.write("x=y".getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new ZipEntry("depensurer/testtarget/TestTarget.class"));
            out.write(1);
        }
        DepDeclaration dep = new DepDeclaration(
            "target",
            "",
            "",
            Collections.<String>emptyList(),
            "depensurertarget",
            "",
            "",
            null,
            null,
            Collections.<String>emptyList(),
            Collections.singletonList("assets/depensurertarget/**"),
            Collections.singletonList("assets/*/lang/**"),
            Arrays.asList(new DepDeclaration.Remap("assets/depensurertarget/", "assets/depensurertest/")),
            true,
            EnsureDep.Side.CLIENT,
            false);
        JarResourcePack pack = new JarResourcePack(jar, dep, "Dep Ensurer: test");

        ResourceLocation marker = new ResourceLocation("depensurertest", "textures/items/marker.png");
        assertTrue(pack.resourceExists(marker));
        try (InputStream in = pack.getInputStream(marker)) {
            byte[] bytes = new byte[3];
            assertEquals(3, in.read(bytes));
            assertArrayEquals("png".getBytes(StandardCharsets.UTF_8), bytes);
        }
        assertFalse(pack.resourceExists(new ResourceLocation("depensurertarget", "textures/items/marker.png")));
        assertFalse(pack.resourceExists(new ResourceLocation("depensurertest", "lang/en_US.lang")));
        assertEquals(Collections.singleton("depensurertest"), pack.getResourceDomains());
        assertEquals("Dep Ensurer: test", pack.getPackName());
        try {
            pack.getInputStream(new ResourceLocation("depensurertest", "missing.png"));
        } catch (FileNotFoundException expected) {
            return;
        }
        throw new AssertionError("Served a missing file");
    }
}
