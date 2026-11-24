package dev.sergiomarin.sitewatch.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import dev.sergiomarin.sitewatch.testsupport.TestServer;

/** Real HTTP(S) round trips against a local server; no Spring context or Docker needed. */
class SiteCheckerTest {

    private TestServer server;
    private CloseableHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = TestServer.http();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
        if (client != null) {
            client.close();
        }
    }

    @Test
    void healthyPageIsUpWithResponseTime() {
        server.page("/", 200, "<html><body>Hello</body></html>");

        CheckResult result = checker(true).check(server.url("/"));

        assertThat(result.up()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.responseTimeMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(result.sslExpiresAt()).isNull();
        assertThat(result.wpVersion()).isNull();
        assertThat(result.error()).isNull();
    }

    @Test
    void serverErrorIsDown() {
        server.page("/", 503, "Maintenance");

        CheckResult result = checker(true).check(server.url("/"));

        assertThat(result.up()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.error()).isEqualTo("HTTP 503");
    }

    @Test
    void followsRedirectsAndReportsFinalStatus() {
        server.page("/old", 301, "", Map.of("Location", server.url("/new/")))
                .page("/new/", 200, "<meta name=\"generator\" content=\"WordPress 6.7.1\">");

        CheckResult result = checker(true).check(server.url("/old"));

        assertThat(result.up()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.wpVersion()).isEqualTo("6.7.1");
    }

    @Test
    void hiddenWordPressVersionFallsBackToFeedUnderTheSiteFolder() {
        server.page("/blog/", 200, "<link rel='stylesheet' href='/blog/wp-content/themes/x/style.css'>")
                .page("/blog/feed/", 200, "<rss><channel><generator>https://wordpress.org/?v=6.6.2</generator>"
                        + "</channel></rss>");

        assertThat(checker(true).check(server.url("/blog/")).wpVersion()).isEqualTo("6.6.2");
    }

    @Test
    void hardenedWordPressFallsBackToLoginPageAssetsAndCachesTheProbe() {
        AtomicInteger loginRequests = new AtomicInteger();
        server.page("/", 200, "<meta name=\"generator\" content=\"Elementor 4.4.1\">", Map.of("Link",
                        "<" + server.url("/wp-json/") + ">; rel=\"https://api.w.org/\""))
                .page("/feed/", 200, "<rss><channel><title>No generator here</title></channel></rss>")
                .page("/readme.html", 200, "<li>PHP version 7.4 or greater.</li>")
                .route("/wp-login.php", exchange -> {
                    loginRequests.incrementAndGet();
                    TestServer.respond(exchange, 200,
                            "<link href='/wp-admin/css/forms.min.css?ver=7.1.5' rel='stylesheet'>", Map.of());
                });
        SiteChecker checker = checker(true);

        assertThat(checker.check(server.url("/")).wpVersion()).isEqualTo("7.1.5");
        assertThat(checker.check(server.url("/")).wpVersion()).isEqualTo("7.1.5");
        assertThat(loginRequests).hasValue(1);
    }

    @Test
    void nonWordPressSiteDoesNotProbeExtraUrls() {
        server.page("/", 200, "<html>Plain site</html>")
                .route("/feed/", exchange -> {
                    throw new AssertionError("feed must not be requested for non-WordPress sites");
                });

        assertThat(checker(true).check(server.url("/")).wpVersion()).isNull();
    }

    @Test
    void connectionRefusedIsDown() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        CheckResult result = checker(true).check("http://127.0.0.1:" + closedPort + "/");

        assertThat(result.up()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(result.error()).isEqualTo("Connection refused");
    }

    @Test
    void slowResponseTimesOut() {
        server.route("/", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TestServer.respond(exchange, 200, "late", Map.of());
        });

        CheckResult result = checker(true, Duration.ofMillis(500)).check(server.url("/"));

        assertThat(result.up()).isFalse();
        assertThat(result.error()).isEqualTo("Response timed out");
    }

    @Test
    void privateAddressesAreBlockedByDefault() {
        server.page("/", 200, "secret internal page");
        SiteChecker guarded = checker(false);

        for (String url : new String[] { server.url("/"), server.url("/").replace("127.0.0.1", "localhost") }) {
            CheckResult result = guarded.check(url);
            assertThat(result.up()).isFalse();
            assertThat(result.httpStatus()).isNull();
            assertThat(result.error()).startsWith("Blocked: ").contains("non-public address");
        }
    }

    @Test
    void readsCertificateExpiryOverHttps(@TempDir Path dir) throws Exception {
        var cert = TestServer.SelfSigned.create(dir, 30);
        try (TestServer https = TestServer.https(cert.serverContext())) {
            https.page("/", 200, "secure");

            CheckResult result = checker(true, Duration.ofSeconds(2), cert.trustingClientContext())
                    .check(https.url("/"));

            assertThat(result.up()).isTrue();
            assertThat(result.sslExpiresAt())
                    .isCloseTo(Instant.now().plus(30, ChronoUnit.DAYS), within(1, ChronoUnit.DAYS));
        }
    }

    @Test
    void untrustedCertificateIsDown(@TempDir Path dir) throws Exception {
        var cert = TestServer.SelfSigned.create(dir, 30);
        try (TestServer https = TestServer.https(cert.serverContext())) {
            https.page("/", 200, "secure");

            CheckResult result = checker(true, Duration.ofSeconds(2), SSLContext.getDefault()).check(https.url("/"));

            assertThat(result.up()).isFalse();
            assertThat(result.error()).startsWith("TLS error: ");
        }
    }

    private SiteChecker checker(boolean allowPrivate) {
        return checker(allowPrivate, Duration.ofSeconds(2));
    }

    private SiteChecker checker(boolean allowPrivate, Duration timeout) {
        try {
            return checker(allowPrivate, timeout, SSLContext.getDefault());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private SiteChecker checker(boolean allowPrivate, Duration timeout, SSLContext sslContext) {
        CheckerProperties properties = new CheckerProperties(false, Duration.ofSeconds(30), 100, 4, timeout,
                timeout, DataSize.ofKilobytes(512), "SitewatchTest/1.0", allowPrivate, Duration.ofDays(90));
        client = CheckHttpClientConfig.create(properties, sslContext);
        return new SiteChecker(client, properties);
    }
}
