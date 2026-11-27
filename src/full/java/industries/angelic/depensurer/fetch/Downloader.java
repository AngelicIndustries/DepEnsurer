package industries.angelic.depensurer.fetch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraftforge.common.config.Configuration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import industries.angelic.depensurer.Fetcher;
import industries.angelic.depensurer.Tags;
import industries.angelic.depensurer.declaration.DepDeclaration;

/**
 * The full build's downloader: Modrinth, then the CurseForge CDN, then the declared mirrors. Every file must match a
 * declared sha1. Dep Ensurer never loads classes from these jars, it only reads their assets.
 */
public class Downloader implements Fetcher {

    static final String USER_AGENT = "AngelicIndustries/DepEnsurer/" + Tags.VERSION
        + " (+https://github.com/AngelicIndustries/DepEnsurer)";
    private static final String CATEGORY = "downloads";

    private final String modrinthApi;
    private final List<String> curseForgeCdns;
    private final Http http;
    private boolean allowDownloads = true;

    public Downloader() {
        this(
            System.getProperty("depensurer.modrinthApi", "https://api.modrinth.com/v2"),
            curseForgeCdns(System.getProperty("depensurer.curseForgeCdn")),
            new Http(USER_AGENT));
    }

    Downloader(String modrinthApi, List<String> curseForgeCdns, Http http) {
        this.modrinthApi = trimSlash(modrinthApi);
        this.curseForgeCdns = curseForgeCdns;
        this.http = http;
    }

    private static List<String> curseForgeCdns(String override) {
        if (override != null && !override.isEmpty()) {
            return Arrays.asList(trimSlash(override));
        }
        return Arrays.asList("https://edge.forgecdn.net/files", "https://mediafilez.forgecdn.net/files");
    }

    @Override
    public void configure(Configuration config) {
        allowDownloads = config.getBoolean(
            "allowDownloads",
            CATEGORY,
            true,
            "Download missing asset source jars from Modrinth, CurseForge and the mirrors mods declare. When off, they're listed on the missing files screen instead. -Ddepensurer.offline=true also turns this off.");
    }

    @Override
    public boolean enabled() {
        return allowDownloads && !Boolean.getBoolean("depensurer.offline");
    }

    @Override
    public boolean canFetch(DepDeclaration dep) {
        return !dep.sha1.isEmpty()
            && (dep.modrinth != null || (dep.curseforge != null && !dep.file.isEmpty()) || !dep.urls.isEmpty());
    }

    @Override
    public File fetch(DepDeclaration dep, File store) throws IOException {
        if (!store.isDirectory() && !store.mkdirs()) {
            throw new IOException("can't create " + store);
        }
        List<String> failures = new ArrayList<>();
        for (Source source : sources(dep, failures)) {
            try {
                return download(source.url, dep, store);
            } catch (IOException e) {
                failures.add(source.label + ": " + describe(e));
            }
        }
        throw new IOException(failures.isEmpty() ? "no source" : String.join("; ", failures));
    }

    private List<Source> sources(DepDeclaration dep, List<String> failures) {
        List<Source> sources = new ArrayList<>();
        if (dep.modrinth != null) {
            try {
                sources.addAll(modrinthFiles(dep));
            } catch (IOException e) {
                failures.add("Modrinth: " + describe(e));
            } catch (RuntimeException e) {
                failures.add("Modrinth: unexpected answer (" + e + ")");
            }
        }
        if (dep.curseforge != null && !dep.file.isEmpty()) {
            int file = dep.curseforge.file;
            for (String cdn : curseForgeCdns) {
                sources.add(
                    new Source("CurseForge", cdn + "/" + file / 1000 + "/" + file % 1000 + "/" + encode(dep.file)));
            }
        }
        for (String url : dep.urls) {
            sources.add(new Source(Http.host(url), url));
        }
        return sources;
    }

    /** The files of the declared Modrinth version whose sha1 the dep declares. */
    private List<Source> modrinthFiles(DepDeclaration dep) throws IOException {
        String json = http.getString(
            modrinthApi + "/project/" + encode(dep.modrinth.project) + "/version/" + encode(dep.modrinth.version));
        JsonObject version = new JsonParser().parse(json)
            .getAsJsonObject();
        List<Source> sources = new ArrayList<>();
        for (JsonElement element : version.getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            String sha1 = file.getAsJsonObject("hashes")
                .get("sha1")
                .getAsString();
            if (dep.sha1.contains(sha1)) {
                sources.add(
                    new Source(
                        "Modrinth",
                        file.get("url")
                            .getAsString()));
            }
        }
        if (sources.isEmpty()) {
            throw new IOException("version " + dep.modrinth.version + " has no file with a declared sha1");
        }
        return sources;
    }

    private File download(String url, DepDeclaration dep, File store) throws IOException {
        File target = target(dep, store);
        File part = new File(store, target.getName() + ".part");
        try {
            String sha1 = http.download(url, part);
            if (!dep.sha1.contains(sha1)) {
                throw new IOException("got a file with sha1 " + sha1 + ", not the declared one");
            }
            try {
                Files.move(
                    part.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(part.toPath());
        }
    }

    /** The dep's file name in the store, unless an unrelated file already has that name. */
    private static File target(DepDeclaration dep, File store) throws IOException {
        String name = dep.file.isEmpty() ? dep.sha1.get(0) + ".jar" : dep.file.replaceAll("[/\\\\:*?\"<>|]", "_");
        File target = new File(store, name);
        if (target.exists() && !dep.sha1.contains(sha1(target))) {
            String base = name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
            target = new File(
                store,
                base + "-"
                    + dep.sha1.get(0)
                        .substring(0, 8)
                    + ".jar");
        }
        return target;
    }

    private static String sha1(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** Short reasons for the missing files screen instead of Java's exception messages. */
    static String describe(IOException e) {
        if (e instanceof UnknownHostException) {
            return "can't resolve " + e.getMessage() + " (offline?)";
        }
        if (e instanceof ConnectException) {
            return "can't connect";
        }
        if (e instanceof SocketTimeoutException) {
            return "timed out";
        }
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static String encode(String segment) {
        try {
            return URLEncoder.encode(segment, "UTF-8")
                .replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static final class Source {

        final String label;
        final String url;

        Source(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }
}
