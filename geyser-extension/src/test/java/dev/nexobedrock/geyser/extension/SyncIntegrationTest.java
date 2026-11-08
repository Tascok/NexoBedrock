package dev.nexobedrock.geyser.extension;

import dev.nexobedrock.sync.SyncBundleWriter;
import dev.nexobedrock.sync.SyncHttpsServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class SyncIntegrationTest {
    @TempDir Path temporary;

    @Test
    void authenticatedHttpsPullStagesThenInstallsAndRejectsInvalidUpdate() throws Exception {
        Path keyStore = temporary.resolve("test.p12");
        String password = "test-password-only";
        Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-storetype", "PKCS12",
                "-keystore", keyStore.toString(), "-storepass", password, "-keypass", password)
                .redirectErrorStream(true).start();
        String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, keytool.waitFor(), output);
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStore)) { trust.load(input, password.toCharArray()); }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(null, factory.getTrustManagers(), null);
        HttpClient http = HttpClient.newBuilder().sslContext(tls).build();
        String token = "0123456789abcdefghijklmnopqrstuvwxyz-test";
        Path pack = temporary.resolve("pack.mcpack");
        createPack(pack);
        byte[] mapping = "{\"format_version\":2,\"items\":{}}".getBytes(StandardCharsets.UTF_8);
        Path bundle = temporary.resolve("latest.zip");
        new SyncBundleWriter().write(pack, "{\"format_version\":2,\"items\":{\"old\":[]}}".getBytes(StandardCharsets.UTF_8), bundle);
        try (SyncHttpsServer server = new SyncHttpsServer(new InetSocketAddress("127.0.0.1", 0), keyStore,
                password.toCharArray(), token, bundle)) {
            server.start();
            URI uri = URI.create("https://localhost:" + server.port() + "/v1/bundle");
            assertThrows(javax.net.ssl.SSLHandshakeException.class, () -> HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.discarding()));
            assertEquals(401, http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(401, http.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer wrong").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(503, http.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode(),
                    "an old bundle must not be served before the current conversion finishes");
            assertEquals(405, http.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, http.send(HttpRequest.newBuilder(URI.create(uri + "?token=secret")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            Properties properties = new Properties(); properties.setProperty("url", uri.toString());
            Path extension = temporary.resolve("proxy/extensions/nexobedrock");
            Path geyser = temporary.resolve("proxy");
            Files.createDirectories(extension);
            Files.writeString(extension.resolve("token.txt"), token);
            String cert = java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(trust.getCertificate("test").getEncoded());
            Files.writeString(extension.resolve("ca.pem"), "-----BEGIN CERTIFICATE-----\n" + cert + "\n-----END CERTIFICATE-----\n");
            properties.setProperty("token-file", "token.txt");
            properties.setProperty("ca-certificate", "ca.pem");
            properties.setProperty("startup-wait-seconds", "10");
            var waiting = new java.util.concurrent.CountDownLatch(1);
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread writer = new Thread(() -> {
                try {
                    if (!waiting.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("retry not observed");
                    new SyncBundleWriter().write(pack, mapping, bundle);
                    server.markReady();
                } catch (Throwable exception) { failure.set(exception); }
            });
            writer.start();
            try (SyncPuller puller = new SyncPuller(extension, properties, message -> {
                if (message.contains("Waiting for")) waiting.countDown();
            })) {
                puller.fetchAtStartup();
                writer.join(10000);
                assertNull(failure.get());
                assertEquals(0, waiting.getCount(), "startup should retry after the initial 503");
                assertFalse(Files.exists(geyser.resolve("packs/nexobedrock.mcpack")));
                puller.applyAtStartup(geyser);
                assertArrayEquals(Files.readAllBytes(pack), Files.readAllBytes(geyser.resolve("packs/nexobedrock.mcpack")));
                assertArrayEquals(mapping, Files.readAllBytes(geyser.resolve("custom_mappings/nexobedrock.json")));
                assertEquals("1=example:item\n", Files.readString(extension.resolve("furniture-mappings.properties")));
                server.beginUpdate();
                assertEquals(503, http.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token).GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode(), "ongoing conversions must not serve stale content");
                server.markReady();
                Files.writeString(bundle, "corrupt");
                assertThrows(java.io.IOException.class, puller::fetch);
                assertArrayEquals(mapping, Files.readAllBytes(geyser.resolve("custom_mappings/nexobedrock.json")));
            }
            var logs = new java.util.ArrayList<String>();
            try (SyncPuller unauthorized = new SyncPuller(extension, properties, logs::add,
                    "wrong-token-012345678901234567890123456789", http)) {
                assertThrows(java.io.IOException.class, unauthorized::fetchAtStartup);
                assertTrue(logs.isEmpty(), "authentication errors must not retry");
            }
        }
    }

    @Test
    void rejectsInsecureHttpUrl() {
        Properties properties = new Properties(); properties.setProperty("url", "http://localhost:8443/v1/bundle");
        assertThrows(IllegalArgumentException.class, () -> new SyncPuller(temporary, properties,
                ignored -> {}, "0123456789abcdefghijklmnopqrstuvwxyz", HttpClient.newHttpClient()));
    }

    @Test
    void rejectsHashMismatchAndUnexpectedArchivePaths() throws Exception {
        Path pack = temporary.resolve("pack.mcpack"); createPack(pack);
        Path original = temporary.resolve("original.zip");
        new SyncBundleWriter().write(pack, "{\"format_version\":2,\"items\":{}}".getBytes(StandardCharsets.UTF_8), original);
        Path modified = temporary.resolve("modified.zip");
        try (var in = new java.util.zip.ZipFile(original.toFile());
             var out = new ZipOutputStream(Files.newOutputStream(modified))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals("mappings.json")) out.write("{}".getBytes(StandardCharsets.UTF_8));
                else try (var data = in.getInputStream(entry)) { data.transferTo(out); }
                out.closeEntry();
            }
        }
        assertThrows(java.io.IOException.class, () -> SyncBundle.read(modified));
        Path traversal = temporary.resolve("traversal.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(traversal))) {
            out.putNextEntry(new ZipEntry("../outside")); out.write(1); out.closeEntry();
        }
        assertThrows(java.io.IOException.class, () -> SyncBundle.read(traversal));
        assertFalse(Files.exists(temporary.getParent().resolve("outside")));
    }

    @Test
    void recoveryRestoresAllPreviousFiles() throws Exception {
        Path geyser = temporary.resolve("proxy");
        Path extension = geyser.resolve("extensions/nexobedrock");
        Path journal = extension.resolve("sync-transaction");
        Files.createDirectories(journal);
        Files.writeString(journal.resolve("old-0"), "old pack");
        Files.writeString(journal.resolve("old-1"), "old mapping");
        Files.writeString(journal.resolve("absent-2"), "");
        Files.writeString(journal.resolve("prepared"), "");
        Files.createDirectories(geyser.resolve("packs"));
        Files.createDirectories(geyser.resolve("custom_mappings"));
        Files.writeString(geyser.resolve("packs/nexobedrock.mcpack"), "new pack");
        Files.writeString(geyser.resolve("custom_mappings/nexobedrock.json"), "new mapping");
        Files.writeString(extension.resolve("furniture-mappings.properties"), "new furniture");
        new SyncInstaller(geyser, extension).recover();
        assertEquals("old pack", Files.readString(geyser.resolve("packs/nexobedrock.mcpack")));
        assertEquals("old mapping", Files.readString(geyser.resolve("custom_mappings/nexobedrock.json")));
        assertFalse(Files.exists(extension.resolve("furniture-mappings.properties")));
    }

    private static void createPack(Path pack) throws Exception {
        try (var out = new ZipOutputStream(Files.newOutputStream(pack))) {
            out.putNextEntry(new ZipEntry("manifest.json"));
            out.write("{\"header\":{\"uuid\":\"a9ef1768-f218-4534-b172-a474d89c739f\"}}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("nexobedrock/furniture-mappings.properties"));
            out.write("1=example:item\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
