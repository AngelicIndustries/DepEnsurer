package industries.angelic.depensurer.fetch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Plain HTTP GETs with redirects across protocols, timeouts and a retry. */
class Http {

    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_JSON = 4L << 20;
    private static final long MAX_JAR = 1L << 30;

    private final String userAgent;
    int connectTimeout = 10_000;
    int readTimeout = 30_000;
    int tries = 2;

    Http(String userAgent) {
        this.userAgent = userAgent;
    }

    String getString(String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        get(url, out, MAX_JSON, null);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Streams the body into the file and returns its SHA-1 as lowercase hex. */
    String download(String url, File to) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (int attempt = 1;; attempt++) {
            digest.reset();
            try (OutputStream out = new FileOutputStream(to)) {
                get(url, out, MAX_JAR, digest);
                break;
            } catch (StatusException | UnknownHostException e) {
                throw e;
            } catch (IOException e) {
                if (attempt >= tries) {
                    throw e;
                }
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private void get(String url, OutputStream out, long limit, MessageDigest digest) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[65536];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new IOException("response from " + host(url) + " is too big");
                }
                out.write(buffer, 0, read);
                if (digest != null) {
                    digest.update(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Follows redirects itself: HttpURLConnection won't go from http to https. */
    private HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int redirects = 0;; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("User-Agent", userAgent);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || redirects >= MAX_REDIRECTS) {
                    throw new IOException("too many redirects from " + host(url));
                }
                current = new URL(new URL(current), location).toString();
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new StatusException(status);
            }
            return connection;
        }
    }

    static String host(String url) {
        try {
            return new URL(url).getHost();
        } catch (IOException e) {
            return url;
        }
    }

    /** A response that retrying won't change. */
    static final class StatusException extends IOException {

        private static final long serialVersionUID = 1L;

        StatusException(int status) {
            super("HTTP " + status);
        }
    }
}
