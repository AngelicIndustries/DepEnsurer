package industries.angelic.depensurer.fetch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;

import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.declaration.DepDeclaration;

public class DownloaderTest {

    private static final byte[] JAR = "the jar's bytes".getBytes(StandardCharsets.UTF_8);
    private static final String SHA1 = sha1(JAR);

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private HttpServer server;
    private String base;
    /** Path to body; a missing path answers 404. */
    private final Map<String, byte[]> responses = new HashMap<>();
    private final Map<String, String> redirects = new HashMap<>();
    private final List<String> requested = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> userAgents = Collections.synchronizedList(new ArrayList<String>());
    private File store;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI()
                .getRawPath();
            requested.add(path);
            userAgents.add(
                exchange.getRequestHeaders()
                    .getFirst("User-Agent"));
            if (redirects.containsKey(path)) {
                exchange.getResponseHeaders()
                    .add("Location", redirects.get(path));
                exchange.sendResponseHeaders(302, -1);
            } else if (responses.containsKey(path)) {
                byte[] body = responses.get(path);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress()
            .getPort();
        store = new File(temp.getRoot(), "depensurer/jars");
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    private Downloader downloader() {
        return new Downloader(base + "/modrinth/", Arrays.asList(base + "/cdn"), new Http(Downloader.USER_AGENT));
    }

    private static DepDeclaration dep(String file, List<String> sha1, DepDeclaration.CurseForge curseforge,
        DepDeclaration.Modrinth modrinth, List<String> urls) {
        return new DepDeclaration(
            "d",
            "",
            file,
            sha1,
            "",
            "",
            "",
            curseforge,
            modrinth,
            urls,
            Collections.singletonList("assets/**"),
            Collections.<String>emptyList(),
            Collections.<DepDeclaration.Remap>emptyList(),
            true,
            EnsureDep.Side.CLIENT,
            false);
    }

    private void modrinthVersion(String sha1, String url) {
        responses.put(
            "/modrinth/project/p/version/v",
            ("{\"files\": [{\"url\": \"" + url
                + "\", \"filename\": \"x.jar\", \"hashes\": {\"sha1\": \""
                + sha1
                + "\", \"sha512\": \"unused\"}}]}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void downloadsFromModrinth() throws Exception {
        modrinthVersion(SHA1, base + "/files/x.jar");
        responses.put("/files/x.jar", JAR);

        File file = downloader().fetch(
            dep(
                "aether.jar",
                Collections.singletonList(SHA1),
                null,
                new DepDeclaration.Modrinth("p", "v"),
                Collections.<String>emptyList()),
            store);
        assertEquals(new File(store, "aether.jar"), file);
        assertArrayEquals(JAR, Files.readAllBytes(file.toPath()));
        assertEquals(Collections.singletonList("aether.jar"), Arrays.asList(store.list()));
        assertTrue(
            userAgents.toString(),
            userAgents.get(0)
                .startsWith("AngelicIndustries/DepEnsurer/"));
    }

    @Test
    public void fallsThroughToTheCurseForgeCdnThenMirrors() throws Exception {
        responses.put("/mirror/x.jar", JAR);

        File file = downloader().fetch(
            dep(
                "a+b c.jar",
                Collections.singletonList(SHA1),
                new DepDeclaration.CurseForge(1, "slug", 2960056),
                new DepDeclaration.Modrinth("p", "v"),
                Collections.singletonList(base + "/mirror/x.jar")),
            store);
        assertArrayEquals(JAR, Files.readAllBytes(file.toPath()));
        assertEquals(
            Arrays.asList("/modrinth/project/p/version/v", "/cdn/2960/56/a%2Bb%20c.jar", "/mirror/x.jar"),
            requested);
    }

    @Test
    public void followsRedirects() throws Exception {
        redirects.put("/mirror/x.jar", "/files/x.jar");
        responses.put("/files/x.jar", JAR);

        File file = downloader().fetch(
            dep(
                "x.jar",
                Collections.singletonList(SHA1),
                null,
                null,
                Collections.singletonList(base + "/mirror/x.jar")),
            store);
        assertArrayEquals(JAR, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void rejectsAFileWithAnotherSha1() throws Exception {
        responses.put("/mirror/x.jar", "something else".getBytes(StandardCharsets.UTF_8));
        try {
            downloader().fetch(
                dep(
                    "x.jar",
                    Collections.singletonList(SHA1),
                    null,
                    null,
                    Collections.singletonList(base + "/mirror/x.jar")),
                store);
            fail("Accepted a file with the wrong sha1");
        } catch (IOException e) {
            assertTrue(
                e.getMessage(),
                e.getMessage()
                    .contains("not the declared one"));
        }
        assertEquals(0, store.list().length);
    }

    @Test
    public void namesEverySourceThatFailed() throws Exception {
        modrinthVersion("0000000000000000000000000000000000000000", base + "/files/x.jar");
        try {
            downloader().fetch(
                dep(
                    "x.jar",
                    Collections.singletonList(SHA1),
                    new DepDeclaration.CurseForge(1, "", 7),
                    new DepDeclaration.Modrinth("p", "v"),
                    Collections.singletonList(base + "/mirror/x.jar")),
                store);
            fail("Downloaded from nowhere");
        } catch (IOException e) {
            assertEquals(
                "Modrinth: version v has no file with a declared sha1; CurseForge: HTTP 404; 127.0.0.1: HTTP 404",
                e.getMessage());
        }
    }

    @Test
    public void saysShortlyWhyItCouldntConnect() throws Exception {
        int closed;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(
            0,
            1,
            java.net.InetAddress.getByName("127.0.0.1"))) {
            closed = socket.getLocalPort();
        }
        try {
            downloader().fetch(
                dep(
                    "x.jar",
                    Collections.singletonList(SHA1),
                    null,
                    null,
                    Collections.singletonList("http://127.0.0.1:" + closed + "/x.jar")),
                store);
            fail("Downloaded from a closed port");
        } catch (IOException e) {
            assertEquals("127.0.0.1: can't connect", e.getMessage());
        }
        assertEquals(
            "can't resolve nowhere.invalid (offline?)",
            Downloader.describe(new java.net.UnknownHostException("nowhere.invalid")));
        assertEquals("timed out", Downloader.describe(new java.net.SocketTimeoutException("Read timed out")));
    }

    @Test
    public void keepsAnUnrelatedFileWithTheSameName() throws Exception {
        store.mkdirs();
        File other = new File(store, "x.jar");
        Files.write(other.toPath(), "unrelated".getBytes(StandardCharsets.UTF_8));
        responses.put("/mirror/x.jar", JAR);

        File file = downloader().fetch(
            dep(
                "x.jar",
                Collections.singletonList(SHA1),
                null,
                null,
                Collections.singletonList(base + "/mirror/x.jar")),
            store);
        assertEquals(new File(store, "x-" + SHA1.substring(0, 8) + ".jar"), file);
        assertArrayEquals("unrelated".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(other.toPath()));
    }

    @Test
    public void needsASha1AndASource() {
        Downloader downloader = downloader();
        List<String> sha1 = Collections.singletonList(SHA1);
        List<String> none = Collections.emptyList();
        assertFalse(downloader.canFetch(dep("x.jar", none, null, new DepDeclaration.Modrinth("p", "v"), none)));
        assertFalse(downloader.canFetch(dep("x.jar", sha1, null, null, none)));
        assertFalse(downloader.canFetch(dep("", sha1, new DepDeclaration.CurseForge(1, "", 2), null, none)));
        assertTrue(downloader.canFetch(dep("x.jar", sha1, new DepDeclaration.CurseForge(1, "", 2), null, none)));
        assertTrue(downloader.canFetch(dep("", sha1, null, null, Collections.singletonList("https://x/y.jar"))));
    }

    @Test
    public void canBeTurnedOff() throws Exception {
        Downloader downloader = downloader();
        // Configuration's constructor reads the game folder from FMLInjectionData, which only the game sets
        java.lang.reflect.Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, temp.getRoot());
        Configuration config = new Configuration(temp.newFile("depensurer.cfg"));
        downloader.configure(config);
        assertTrue(downloader.enabled());
        config.get("downloads", "allowDownloads", true)
            .set(false);
        downloader.configure(config);
        assertFalse(downloader.enabled());

        config.get("downloads", "allowDownloads", true)
            .set(true);
        downloader.configure(config);
        System.setProperty("depensurer.offline", "true");
        try {
            assertFalse(downloader.enabled());
        } finally {
            System.clearProperty("depensurer.offline");
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-1")
                .digest(bytes)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
