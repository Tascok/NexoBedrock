package dev.nexobedrock.geyser.extension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Downloads validated bundles. Installation only happens during startup. */
public final class SyncPuller implements AutoCloseable {
    private static final long MAX_DOWNLOAD = 240L * 1024 * 1024;
    private final Path directory;
    private final URI url;
    private final String token;
    private final long interval;
    private final long startupWaitSeconds;
    private final Consumer<String> log;
    private final HttpClient client;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "NexoBedrock-sync-poll"); thread.setDaemon(true); return thread;
    });

    public SyncPuller(Path directory, Properties settings, Consumer<String> log) {
        this(directory, settings, log, loadToken(directory, settings), makeHttpClient(directory, settings));
    }

    SyncPuller(Path directory, Properties settings, Consumer<String> log, String configuredToken, HttpClient httpClient) {
        this.directory = directory;
        this.log = log;
        client = httpClient == null ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build() : httpClient;
        url = URI.create(settings.getProperty("url", ""));
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                || url.getUserInfo() != null || url.getRawQuery() != null || url.getFragment() != null)
            throw new IllegalArgumentException("Sync URL must be HTTPS without credentials, query or fragment");
        token = configuredToken;
        if (token == null || !token.matches("[A-Za-z0-9_-]{32,256}"))
            throw new IllegalArgumentException("Missing or invalid sync token environment variable");
        interval = Math.max(30, Long.parseLong(settings.getProperty("interval-seconds", "60")));
        startupWaitSeconds = Math.max(0, Math.min(300,
                Long.parseLong(settings.getProperty("startup-wait-seconds", "90"))));
    }

    public void fetch() throws IOException, InterruptedException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".sync-download-", ".tmp");
        try {
            HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200) throw new SyncHttpException(response.statusCode());
                long count = 0;
                try (var out = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = input.read(buffer)) != -1) {
                        count += n;
                        if (count > MAX_DOWNLOAD) throw new IOException("Sync download exceeds limit");
                        out.write(buffer, 0, n);
                    }
                }
            }
            SyncBundle.read(temporary);
            Path staged = directory.resolve("sync-pending.zip");
            String hash = hash(temporary);
            if (Files.exists(staged) && hash.equals(hash(staged))) return;
            move(temporary, staged);
            log.accept("New synchronized content downloaded and verified; restart Geyser to apply it.");
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Wait for the source to start before Geyser loads registries, then fall back to the last bundle. */
    public void fetchAtStartup() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(startupWaitSeconds);
        boolean announced = false;
        while (true) {
            try {
                fetch();
                return;
            } catch (javax.net.ssl.SSLException exception) {
                throw exception; // Certificate problems require configuration changes, not retries.
            } catch (IOException exception) {
                if (exception instanceof SyncHttpException status
                        && status.status >= 400 && status.status < 500 && status.status != 429) throw exception;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw exception;
                if (!announced) {
                    log.accept("Waiting for the Paper sync endpoint before loading Geyser content.");
                    announced = true;
                }
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.SECONDS.toNanos(2)));
            }
        }
    }

    private static final class SyncHttpException extends IOException {
        private final int status;
        private SyncHttpException(int status) { super("Sync server returned HTTP " + status); this.status = status; }
    }

    public void applyAtStartup(Path geyserDirectory) throws IOException {
        SyncInstaller installer = new SyncInstaller(geyserDirectory, directory);
        installer.recover();
        Path staged = directory.resolve("sync-pending.zip");
        if (!Files.isRegularFile(staged)) return;
        String hash = hash(staged);
        if (hash.equals(readAppliedHash())) return;
        installer.install(SyncBundle.read(staged));
        Path marker = Files.createTempFile(directory, ".sync-applied-", ".tmp");
        try { Files.writeString(marker, hash); move(marker, directory.resolve("sync-applied.sha256")); }
        finally { Files.deleteIfExists(marker); }
        log.accept("Synchronized pack and mappings installed before Geyser initialization.");
    }

    public void start() {
        executor.scheduleWithFixedDelay(() -> {
            try { fetch(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            catch (Exception exception) { log.accept("Proxy sync download failed (" + exception.getClass().getSimpleName() + "); retrying later."); }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private String readAppliedHash() throws IOException {
        Path marker = directory.resolve("sync-applied.sha256");
        return Files.isRegularFile(marker) ? Files.readString(marker).trim() : "";
    }

    private static String hash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException exception) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String loadToken(Path directory, Properties settings) {
        String file = settings.getProperty("token-file", "").trim();
        if (file.isEmpty()) return System.getenv(settings.getProperty("token-env", "NEXOBEDROCK_SYNC_TOKEN"));
        try { return Files.readString(directory.resolve(file).normalize()).strip(); }
        catch (IOException exception) { throw new IllegalArgumentException("Could not read sync token file", exception); }
    }

    private static HttpClient makeHttpClient(Path directory, Properties settings) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER);
        String certificate = settings.getProperty("ca-certificate", "").trim();
        if (!certificate.isEmpty()) {
            try (var input = Files.newInputStream(directory.resolve(certificate).normalize())) {
                var cert = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input);
                var trust = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
                trust.load(null, null);
                trust.setCertificateEntry("sync-ca", cert);
                var factory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                factory.init(trust);
                var tls = javax.net.ssl.SSLContext.getInstance("TLS");
                tls.init(null, factory.getTrustManagers(), null);
                builder.sslContext(tls);
            } catch (Exception exception) { throw new IllegalArgumentException("Could not load sync CA certificate", exception); }
        }
        return builder.build();
    }
    @Override public void close() { executor.shutdownNow(); }
}
