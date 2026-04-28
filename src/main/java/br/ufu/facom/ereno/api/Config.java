package br.ufu.facom.ereno.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Read/write HTTP API for ERENO config files.
 * 
 * Endpoints:
 *   GET    /api/health
 *   GET    /api/configs              list all .json under config/
 *   GET    /api/configs/tree         grouped listing
 *   GET    /api/configs/{relpath}    fetch one file (200 + ETag header)
 *   PUT    /api/configs/{relpath}    create or replace; body must parse as JSON
 *   DELETE /api/configs/{relpath}    remove file (204)
 *   GET    /api/artifacts            list .arff/.csv/.model/.json/.txt/.log under target/
 *   GET    /api/artifacts/{relpath}  download one artifact
 *
 * Run:
 *   java -cp target/classes br.ufu.facom.ereno.api.Config
 */
public final class Config {

    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());
    private static final Gson GSON = new Gson();

    private Config() {}
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getProperty("ereno.api.port", "8080"));
        String bind = firstNonEmpty(
                System.getenv("ERENO_API_BIND"),
                System.getProperty("ereno.api.bind"),
                "127.0.0.1");

        Path configRoot = Paths.get(System.getProperty("ereno.api.config", "config"))
                .toAbsolutePath().normalize();
        Path artifactsRoot = Paths.get(System.getProperty("ereno.api.artifacts", "target"))
                .toAbsolutePath().normalize();
        boolean backupsEnabled = !"false".equalsIgnoreCase(
                System.getProperty("ereno.api.backups", "true"));
        Path backupRoot = backupsEnabled ? artifactsRoot.resolve("config_backups") : null;

        if (!isLoopback(bind) && (System.getenv("ERENO_API_TOKEN") == null
                || System.getenv("ERENO_API_TOKEN").isEmpty())) {
            System.err.println("Refusing to bind " + bind + " without ERENO_API_TOKEN set.");
            System.exit(2);
        }

        HttpServer server = start(bind, port, configRoot, artifactsRoot, backupRoot);

        LOGGER.info(() -> "ERENO config API listening on http://" + bind + ":" + port);
        LOGGER.info(() -> "  config root:    " + configRoot);
        LOGGER.info(() -> "  artifacts root: " + artifactsRoot);
        LOGGER.info(() -> "  backups:        " + (backupRoot == null ? "disabled" : backupRoot));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down API server");
            server.stop(1);
        }, "api-shutdown"));
    }

    /**
     * {@link #main(String[])}.
     */
    public static HttpServer start(String bind, int port,
                                   Path configRoot, Path artifactsRoot, Path backupRoot) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 0);

        server.createContext("/api/health", (HttpExchange ex) -> {
            if (Http.handlePreflight(ex)) return;
            String body = "{\"ok\":true,\"configRoot\":" + Http.jsonString(configRoot.toString())
                    + ",\"artifactsRoot\":" + Http.jsonString(artifactsRoot.toString()) + "}";
            Http.sendJson(ex, 200, body);
        });

        server.createContext("/api/configs",
                new ConfigCrudHandler(configRoot, "/api/configs", true, backupRoot));
        server.createContext("/api/artifacts",
                new ArtifactReadHandler(artifactsRoot, "/api/artifacts"));

        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "api-worker");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return server;
    }

    private static boolean isLoopback(String addr) {
        return "127.0.0.1".equals(addr) || "localhost".equalsIgnoreCase(addr) || "::1".equals(addr);
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    public static final class PathSafety {

        private PathSafety() {}

        public static Path resolveSafe(Path root, String relPath) {
            if (relPath == null || relPath.isEmpty()) {
                return null;
            }
            String normalized = relPath.replace('\\', '/');
            if (normalized.startsWith("/")) {
                return null;
            }
            for (String seg : normalized.split("/")) {
                if (seg.equals("..")) {
                    return null;
                }
            }

            Path absRoot = root.toAbsolutePath().normalize();
            Path resolved = absRoot.resolve(normalized).normalize().toAbsolutePath();
            if (!resolved.startsWith(absRoot)) {
                return null;
            }

            try {
                if (Files.exists(resolved)) {
                    Path real = resolved.toRealPath();
                    if (!real.startsWith(absRoot.toRealPath())) {
                        return null;
                    }
                }
            } catch (IOException ignored) {
                return null;
            }

            return resolved;
        }

        public static void atomicWrite(Path target, byte[] bytes) throws IOException {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = (parent != null ? parent : Path.of("."))
                    .resolve(target.getFileName().toString() + ".tmp-" + UUID.randomUUID());
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        }

        public static String sha256Hex(byte[] bytes) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(bytes);
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                    sb.append(Character.forDigit(b & 0xF, 16));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }

    static final class Http {

        private Http() {}

        static byte[] readBody(HttpExchange ex) throws IOException {
            try (InputStream in = ex.getRequestBody()) {
                return in.readAllBytes();
            }
        }

        static void sendBytes(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", contentType);
            addCors(ex);
            if (body.length == 0 || status == 204) {
                ex.sendResponseHeaders(status, -1);
            } else {
                ex.sendResponseHeaders(status, body.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(body);
                }
            }
        }

        static void sendJson(HttpExchange ex, int status, String json) throws IOException {
            sendBytes(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        }

        static void sendError(HttpExchange ex, int status, String message) throws IOException {
            String json = "{\"error\":" + jsonString(message) + ",\"status\":" + status + "}";
            sendJson(ex, status, json);
        }

        static String jsonString(String s) {
            if (s == null) return "null";
            StringBuilder sb = new StringBuilder(s.length() + 2);
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"':  sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n");  break;
                    case '\r': sb.append("\\r");  break;
                    case '\t': sb.append("\\t");  break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
            return sb.toString();
        }

        static void addCors(HttpExchange ex) {
            Headers h = ex.getResponseHeaders();
            String origin = System.getenv().getOrDefault("ERENO_API_CORS_ORIGIN", "http://localhost:5173");
            h.set("Access-Control-Allow-Origin", origin);
            h.set("Access-Control-Allow-Methods", "GET,PUT,DELETE,OPTIONS");
            h.set("Access-Control-Allow-Headers", "Content-Type,If-Match,Authorization");
            h.set("Access-Control-Expose-Headers", "ETag");
            h.set("Vary", "Origin");
        }

        static boolean handlePreflight(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return true;
            }
            return false;
        }

        /**
         * @return true if the request should proceed
         */
        static boolean checkAuth(HttpExchange ex) throws IOException {
            String required = System.getenv("ERENO_API_TOKEN");
            if (required == null || required.isEmpty()) {
                return true;
            }
            String header = ex.getRequestHeaders().getFirst("Authorization");
            if (header != null && header.startsWith("Bearer ") && header.substring(7).equals(required)) {
                return true;
            }
            sendError(ex, 401, "Missing or invalid bearer token");
            return false;
        }
    }

    static final class ConfigCrudHandler implements HttpHandler {

        private static final Logger LOG = Logger.getLogger(ConfigCrudHandler.class.getName());

        private final Path root;
        private final String basePrefix;
        private final boolean writable;
        private final Path backupRoot; // null when backups disabled

        ConfigCrudHandler(Path root, String basePrefix, boolean writable, Path backupRoot) {
            this.root = root.toAbsolutePath().normalize();
            this.basePrefix = basePrefix.endsWith("/")
                    ? basePrefix.substring(0, basePrefix.length() - 1)
                    : basePrefix;
            this.writable = writable;
            this.backupRoot = backupRoot;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if (Http.handlePreflight(ex)) return;
                if (!Http.checkAuth(ex)) return;

                String path = ex.getRequestURI().getPath();
                String rel = stripPrefix(path);
                String method = ex.getRequestMethod();

                if (rel == null) {
                    Http.sendError(ex, 404, "Not found");
                    return;
                }

                if (rel.isEmpty() || rel.equals("/")) {
                    if ("GET".equalsIgnoreCase(method)) {
                        handleList(ex);
                    } else {
                        Http.sendError(ex, 405, "Method not allowed");
                    }
                    return;
                }
                if (rel.equals("/tree")) {
                    if ("GET".equalsIgnoreCase(method)) {
                        handleTree(ex);
                    } else {
                        Http.sendError(ex, 405, "Method not allowed");
                    }
                    return;
                }

                String relPath = rel.startsWith("/") ? rel.substring(1) : rel;

                if (!relPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    Http.sendError(ex, 400, "Only .json files are supported");
                    return;
                }
                Path resolved = PathSafety.resolveSafe(root, relPath);
                if (resolved == null) {
                    Http.sendError(ex, 400, "Invalid path");
                    return;
                }

                switch (method.toUpperCase(Locale.ROOT)) {
                    case "GET":
                        handleGet(ex, relPath, resolved);
                        break;
                    case "PUT":
                        if (!writable) {
                            Http.sendError(ex, 405, "Read-only endpoint");
                            return;
                        }
                        handlePut(ex, relPath, resolved);
                        break;
                    case "DELETE":
                        if (!writable) {
                            Http.sendError(ex, 405, "Read-only endpoint");
                            return;
                        }
                        handleDelete(ex, resolved);
                        break;
                    default:
                        Http.sendError(ex, 405, "Method not allowed");
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Handler error", e);
                Http.sendError(ex, 500, "Internal error: " + e.getClass().getSimpleName());
            }
        }

        private String stripPrefix(String path) {
            if (path.equals(basePrefix)) return "";
            if (path.startsWith(basePrefix + "/")) return path.substring(basePrefix.length());
            return null;
        }

        private void handleList(HttpExchange ex) throws IOException {
            List<String> files = collectJsonFiles();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("root", root.toString());
            body.put("files", files);
            Http.sendJson(ex, 200, GSON.toJson(body));
        }

        private void handleTree(HttpExchange ex) throws IOException {
            List<String> files = collectJsonFiles();
            Map<String, List<String>> grouped = new LinkedHashMap<>();
            for (String rel : files) {
                int slash = rel.indexOf('/');
                String key;
                if (slash < 0) {
                    key = "root";
                } else {
                    int second = rel.indexOf('/', slash + 1);
                    key = (second < 0) ? rel.substring(0, slash) : rel.substring(0, second);
                }
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(rel);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("root", root.toString());
            body.put("groups", grouped);
            Http.sendJson(ex, 200, GSON.toJson(body));
        }

        private List<String> collectJsonFiles() throws IOException {
            if (!Files.isDirectory(root)) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
            }
            Collections.sort(out);
            return out;
        }

        private void handleGet(HttpExchange ex, String relPath, Path resolved) throws IOException {
            if (!Files.isRegularFile(resolved)) {
                Http.sendError(ex, 404, "File not found: " + relPath);
                return;
            }
            byte[] bytes = Files.readAllBytes(resolved);
            String etag = "\"" + PathSafety.sha256Hex(bytes) + "\"";
            ex.getResponseHeaders().set("ETag", etag);

            String inm = ex.getRequestHeaders().getFirst("If-None-Match");
            if (etag.equals(inm)) {
                Http.addCors(ex);
                ex.sendResponseHeaders(304, -1);
                return;
            }
            Http.sendBytes(ex, 200, "application/json; charset=utf-8", bytes);
        }

        private void handlePut(HttpExchange ex, String relPath, Path resolved) throws IOException {
            byte[] body = Http.readBody(ex);
            if (body.length == 0) {
                Http.sendError(ex, 400, "Empty body");
                return;
            }

            try (Reader r = new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
                JsonParser.parseReader(r);
            } catch (JsonSyntaxException | IOException e) {
                Http.sendError(ex, 400, "Invalid JSON: " + e.getMessage());
                return;
            }

            String ifMatch = ex.getRequestHeaders().getFirst("If-Match");
            if (ifMatch != null && Files.isRegularFile(resolved)) {
                String currentEtag = "\"" + PathSafety.sha256Hex(Files.readAllBytes(resolved)) + "\"";
                if (!ifMatch.equals(currentEtag)) {
                    Http.sendError(ex, 412, "If-Match precondition failed");
                    return;
                }
            }

            if (backupRoot != null && Files.isRegularFile(resolved)) {
                try {
                    Path backupTarget = backupRoot
                            .resolve(relPath + "." + System.currentTimeMillis() + ".json");
                    Files.createDirectories(backupTarget.getParent());
                    Files.copy(resolved, backupTarget);
                } catch (IOException be) {
                    LOG.log(Level.WARNING, "Backup failed for " + relPath, be);
                }
            }

            PathSafety.atomicWrite(resolved, body);

            String newEtag = "\"" + PathSafety.sha256Hex(body) + "\"";
            ex.getResponseHeaders().set("ETag", newEtag);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("path", relPath);
            resp.put("etag", newEtag);
            resp.put("bytes", body.length);
            Http.sendJson(ex, 200, GSON.toJson(resp));
        }

        private void handleDelete(HttpExchange ex, Path resolved) throws IOException {
            if (!Files.isRegularFile(resolved)) {
                Http.sendError(ex, 404, "File not found");
                return;
            }
            Files.delete(resolved);
            Http.sendBytes(ex, 204, "application/json", new byte[0]);
        }
    }

    static final class ArtifactReadHandler implements HttpHandler {

        private static final Logger LOG = Logger.getLogger(ArtifactReadHandler.class.getName());
        private static final Set<String> ALLOWED_EXT =
                Set.of(".arff", ".csv", ".model", ".json", ".txt", ".log");

        private final Path root;
        private final String basePrefix;

        ArtifactReadHandler(Path root, String basePrefix) {
            this.root = root.toAbsolutePath().normalize();
            this.basePrefix = basePrefix.endsWith("/")
                    ? basePrefix.substring(0, basePrefix.length() - 1)
                    : basePrefix;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if (Http.handlePreflight(ex)) return;
                if (!Http.checkAuth(ex)) return;
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    Http.sendError(ex, 405, "Read-only endpoint");
                    return;
                }

                String path = ex.getRequestURI().getPath();
                String rel = stripPrefix(path);
                if (rel == null) {
                    Http.sendError(ex, 404, "Not found");
                    return;
                }

                if (rel.isEmpty() || rel.equals("/")) {
                    handleList(ex);
                    return;
                }

                String relPath = rel.startsWith("/") ? rel.substring(1) : rel;
                if (!hasAllowedExt(relPath)) {
                    Http.sendError(ex, 400, "Extension not allowed");
                    return;
                }

                Path resolved = PathSafety.resolveSafe(root, relPath);
                if (resolved == null) {
                    Http.sendError(ex, 400, "Invalid path");
                    return;
                }
                if (!Files.isRegularFile(resolved)) {
                    Http.sendError(ex, 404, "File not found: " + relPath);
                    return;
                }

                byte[] bytes = Files.readAllBytes(resolved);
                Http.sendBytes(ex, 200, guessContentType(relPath), bytes);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Artifact handler error", e);
                Http.sendError(ex, 500, "Internal error: " + e.getClass().getSimpleName());
            }
        }

        private String stripPrefix(String path) {
            if (path.equals(basePrefix)) return "";
            if (path.startsWith(basePrefix + "/")) return path.substring(basePrefix.length());
            return null;
        }

        private void handleList(HttpExchange ex) throws IOException {
            List<String> files = new ArrayList<>();
            if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> hasAllowedExt(p.getFileName().toString()))
                        .forEach(p -> files.add(root.relativize(p).toString().replace('\\', '/')));
                }
            }
            Collections.sort(files);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("root", root.toString());
            body.put("files", files);
            Http.sendJson(ex, 200, GSON.toJson(body));
        }

        private static boolean hasAllowedExt(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String ext : ALLOWED_EXT) {
                if (lower.endsWith(ext)) return true;
            }
            return false;
        }

        private static String guessContentType(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".json")) return "application/json; charset=utf-8";
            if (lower.endsWith(".csv"))  return "text/csv; charset=utf-8";
            if (lower.endsWith(".arff") || lower.endsWith(".txt") || lower.endsWith(".log"))
                return "text/plain; charset=utf-8";
            return "application/octet-stream";
        }
    }
}
