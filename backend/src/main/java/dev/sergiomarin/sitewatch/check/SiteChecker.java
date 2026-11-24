package dev.sergiomarin.sitewatch.check;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Performs one check: fetches the URL (following redirects), measures the time
 * to the response headers, reads the certificate expiry and looks for the
 * WordPress version. Never throws; failures become a down result with a reason.
 */
@Component
public class SiteChecker {

    private static final String ACCEPT = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8";
    static final Duration PROBE_TTL = Duration.ofHours(12);

    private final CloseableHttpClient client;
    private final int maxBodyBytes;
    /** Hidden WordPress versions found by probing, per site folder. Bounded by the number of sites. */
    private final Map<URI, ProbedVersion> probedVersions = new ConcurrentHashMap<>();

    public SiteChecker(CloseableHttpClient checkHttpClient, CheckerProperties properties) {
        this.client = checkHttpClient;
        this.maxBodyBytes = (int) Math.min(Integer.MAX_VALUE, properties.maxBodyBytes().toBytes());
    }

    public CheckResult check(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return CheckResult.failure("Invalid URL");
        }
        HttpClientContext context = HttpClientContext.create();
        long start = System.nanoTime();
        Page page;
        try {
            page = client.execute(get(uri), context, response -> {
                int elapsedMs = (int) ((System.nanoTime() - start) / 1_000_000);
                return new Page(response.getCode(), elapsedMs, readBody(response), linkHeader(response),
                        certificateExpiry(context.getSSLSession()));
            });
        } catch (IOException e) {
            return CheckResult.failure(describe(e, uri));
        }

        boolean up = page.status() < 400;
        String wpVersion = up ? detectWordPress(finalUri(uri, context), page) : null;
        return new CheckResult(up, page.status(), page.responseTimeMs(), page.sslExpiresAt(), wpVersion,
                up ? null : "HTTP " + page.status());
    }

    private String detectWordPress(URI pageUri, Page page) {
        Optional<String> version = WordPressVersions.fromGeneratorMeta(page.body());
        if (version.isPresent() || !WordPressVersions.looksLikeWordPress(page.body(), page.link())) {
            return version.orElse(null);
        }
        // WordPress with the version hidden. Probing takes extra requests (one of them to
        // wp-login.php, which security plugins watch), so the answer is reused for a while.
        URI base = directoryOf(pageUri);
        Instant now = Instant.now();
        ProbedVersion cached = probedVersions.get(base);
        if (cached != null && cached.probedAt().plus(PROBE_TTL).isAfter(now)) {
            return cached.version();
        }
        String probed = probeHiddenVersion(base);
        probedVersions.put(base, new ProbedVersion(probed, now));
        return probed;
    }

    private String probeHiddenVersion(URI base) {
        return fetchText(base.resolve("feed/")).flatMap(WordPressVersions::fromFeed)
                .or(() -> fetchText(base.resolve("readme.html")).flatMap(WordPressVersions::fromReadme))
                .or(() -> fetchText(base.resolve("wp-login.php")).flatMap(WordPressVersions::fromCoreAssets))
                .orElse(null);
    }

    private Optional<String> fetchText(URI uri) {
        try {
            return client.execute(get(uri), response -> response.getCode() < 300
                    ? Optional.ofNullable(readBody(response))
                    : Optional.<String>empty());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static HttpGet get(URI uri) {
        HttpGet request = new HttpGet(uri);
        request.setHeader(HttpHeaders.ACCEPT, ACCEPT);
        return request;
    }

    private String readBody(ClassicHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return null;
        }
        Charset charset = Optional.ofNullable(ContentType.parseLenient(entity.getContentType()))
                .map(ContentType::getCharset)
                .orElse(StandardCharsets.UTF_8);
        try (InputStream in = entity.getContent()) {
            return new String(in.readNBytes(maxBodyBytes), charset);
        }
    }

    private static String linkHeader(ClassicHttpResponse response) {
        Header header = response.getFirstHeader("Link");
        return header == null ? null : header.getValue();
    }

    private static Instant certificateExpiry(SSLSession session) {
        if (session == null) {
            return null;
        }
        try {
            Certificate[] chain = session.getPeerCertificates();
            return chain.length > 0 && chain[0] instanceof X509Certificate leaf
                    ? leaf.getNotAfter().toInstant()
                    : null;
        } catch (SSLPeerUnverifiedException e) {
            return null;
        }
    }

    private static URI finalUri(URI requested, HttpClientContext context) {
        List<URI> redirects = context.getRedirectLocations() == null ? List.of()
                : context.getRedirectLocations().getAll();
        return redirects.isEmpty() ? requested : redirects.getLast();
    }

    /** The "directory" of a page, so WordPress in a subfolder resolves {@code feed/} under it. */
    private static URI directoryOf(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        } else if (!path.endsWith("/")) {
            path = path.substring(0, path.lastIndexOf('/') + 1);
        }
        return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + path);
    }

    private static String describe(IOException e, URI uri) {
        return switch (e) {
            case BlockedAddressException blocked -> "Blocked: " + blocked.getMessage();
            case UnknownHostException unknown -> "DNS lookup failed for " + uri.getHost();
            case ConnectTimeoutException timeout -> "Connection timed out";
            case HttpHostConnectException refused -> "Connection refused";
            case SocketTimeoutException timeout -> "Response timed out";
            case SSLException ssl -> "TLS error: " + ssl.getMessage();
            case ClientProtocolException protocol -> "HTTP protocol error: "
                    + Optional.ofNullable(protocol.getCause()).map(Throwable::getMessage).orElse(protocol.getMessage());
            default -> e.getClass().getSimpleName() + ": " + e.getMessage();
        };
    }

    private record ProbedVersion(String version, Instant probedAt) {
    }

    private record Page(int status, int responseTimeMs, String body, String link, Instant sslExpiresAt) {
    }
}
