package dev.sergiomarin.sitewatch.check;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/** Tiny HTTP(S) server on 127.0.0.1 for exercising real checks. */
final class TestServer implements AutoCloseable {

    private static final char[] PASSWORD = "changeit".toCharArray();

    private final HttpServer server;

    private TestServer(HttpServer server) {
        this.server = server;
        server.start();
    }

    static TestServer http() throws IOException {
        return new TestServer(HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
    }

    static TestServer https(SSLContext serverContext) throws IOException {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        return new TestServer(server);
    }

    TestServer route(String path, HttpHandler handler) {
        server.createContext(path, handler);
        return this;
    }

    TestServer page(String path, int status, String body) {
        return page(path, status, body, Map.of());
    }

    TestServer page(String path, int status, String body, Map<String, String> headers) {
        return route(path, exchange -> respond(exchange, status, body, headers));
    }

    String url(String path) {
        String scheme = server instanceof HttpsServer ? "https" : "http";
        return scheme + "://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    static void respond(HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws IOException {
        try (exchange) {
            headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /** A self-signed certificate for 127.0.0.1/localhost, made with the JDK's keytool. */
    record SelfSigned(SSLContext serverContext, SSLContext trustingClientContext) {

        static SelfSigned create(Path dir, int validityDays) throws Exception {
            Path keystore = dir.resolve("server.p12");
            Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
            Process process = new ProcessBuilder(List.of(keytool.toString(), "-genkeypair", "-alias", "server",
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", String.valueOf(validityDays),
                    "-dname", "CN=localhost", "-ext", "san=ip:127.0.0.1,dns:localhost",
                    "-keystore", keystore.toString(), "-storetype", "PKCS12",
                    "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD)))
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("keytool failed: " + output);
            }
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystore)) {
                store.load(in, PASSWORD);
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, PASSWORD);
            SSLContext server = SSLContext.getInstance("TLS");
            server.init(keys.getKeyManagers(), null, null);

            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext client = SSLContext.getInstance("TLS");
            client.init(null, trust.getTrustManagers(), null);
            return new SelfSigned(server, client);
        }
    }
}
