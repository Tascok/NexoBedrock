package dev.nexobedrock.sync;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only authenticated endpoint; caller owns TLS certificate and secret provisioning. */
public final class SyncHttpsServer implements AutoCloseable {
    private final HttpsServer server;
    private final ExecutorService executor;
    private final java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();

    public SyncHttpsServer(InetSocketAddress bind, Path keyStorePath, char[] password,
                           String token, Path bundle) throws Exception {
        if (token == null || !token.matches("[A-Za-z0-9_-]{32,256}"))
            throw new IllegalArgumentException("Synchronization token must contain 32–256 URL-safe characters");
        byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStorePath)) { keys.load(input, password); }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, password);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(managers.getKeyManagers(), null, null);
        server = HttpsServer.create(bind, 16);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "NexoBedrock-sync-https");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/v1/bundle", exchange -> {
            try {
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                if (!"/v1/bundle".equals(exchange.getRequestURI().getPath())
                        || exchange.getRequestURI().getRawQuery() != null) {
                    exchange.sendResponseHeaders(404, -1);
                } else if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                } else {
                    String auth = exchange.getRequestHeaders().getFirst("Authorization");
                    byte[] given = auth == null ? new byte[0] : auth.getBytes(StandardCharsets.UTF_8);
                    if (!MessageDigest.isEqual(expected, given)) {
                        exchange.sendResponseHeaders(401, -1);
                    } else if (!ready.get() || !Files.isRegularFile(bundle)) {
                        exchange.sendResponseHeaders(503, -1);
                    } else {
                        try (var channel = java.nio.channels.FileChannel.open(bundle)) {
                            long size = channel.size();
                            exchange.getResponseHeaders().set("Content-Type", "application/zip");
                            exchange.sendResponseHeaders(200, size);
                            java.nio.channels.Channels.newInputStream(channel).transferTo(exchange.getResponseBody());
                        }
                    }
                    Arrays.fill(given, (byte) 0);
                }
            } catch (IOException ignored) {
                // Client disconnected, or the response could not be sent.
            } finally { exchange.close(); }
        });
    }

    public void start() { server.start(); }
    public void beginUpdate() { ready.set(false); }
    public void markReady() { ready.set(true); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
