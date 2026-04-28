import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import br.ufu.facom.ereno.api.Config;

public class ConfigTest {

    private HttpServer server;
    private Path configRoot;
    private Path artifactsRoot;
    private Path backupRoot;
    private int port;

    @BeforeEach
    void start() throws IOException {
        configRoot = Files.createTempDirectory("api-cfg");
        artifactsRoot = Files.createTempDirectory("api-art");
        backupRoot = artifactsRoot.resolve("config_backups");

        // Seed a sample config
        Path attacks = configRoot.resolve("attacks");
        Files.createDirectories(attacks);
        Files.writeString(attacks.resolve("sample.json"),
                "{\n  \"attackType\": \"random_replay\",\n  \"enabled\": true\n}\n");

        server = Config.start("127.0.0.1", 0, configRoot, artifactsRoot, backupRoot);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() throws IOException {
        if (server != null) server.stop(0);
        deleteRecursive(configRoot);
        deleteRecursive(artifactsRoot);
    }

    @Test
    void healthEndpoint() throws IOException {
        Response r = http("GET", "/api/health", null, null);
        assertEquals(200, r.status);
        assertTrue(r.body.contains("\"ok\":true"));
    }

    @Test
    void listAndGetRoundTrip() throws IOException {
        Response list = http("GET", "/api/configs", null, null);
        assertEquals(200, list.status);
        assertTrue(list.body.contains("attacks/sample.json"), list.body);

        Response get = http("GET", "/api/configs/attacks/sample.json", null, null);
        assertEquals(200, get.status);
        assertTrue(get.body.contains("\"attackType\""));
        assertNotNull(get.etag);
    }

    @Test
    void putValidJsonWritesAndReturnsEtag() throws IOException {
        String body = "{\"attackType\":\"random_replay\",\"enabled\":false}";
        Response put = http("PUT", "/api/configs/attacks/new.json", body, null);
        assertEquals(200, put.status);
        assertTrue(Files.exists(configRoot.resolve("attacks/new.json")));

        Response get = http("GET", "/api/configs/attacks/new.json", null, null);
        assertEquals(200, get.status);
        assertEquals(body, get.body.trim());
    }

    @Test
    void putRejectsInvalidJson() throws IOException {
        Response r = http("PUT", "/api/configs/attacks/bad.json", "not json at all", null);
        assertEquals(400, r.status);
        assertFalse(Files.exists(configRoot.resolve("attacks/bad.json")));
    }

    @Test
    void putRejectsNonJsonExtension() throws IOException {
        Response r = http("PUT", "/api/configs/attacks/bad.txt", "{}", null);
        assertEquals(400, r.status);
    }

    @Test
    void putRejectsTraversal() throws IOException {
        Response r = http("PUT", "/api/configs/../escape.json", "{}", null);
        assertEquals(400, r.status);
    }

    @Test
    void ifMatchEnforcesConcurrency() throws IOException {
        Response first = http("GET", "/api/configs/attacks/sample.json", null, null);
        String etag = first.etag;
        assertNotNull(etag);

        // Stale ETag must be rejected
        Response stale = http("PUT", "/api/configs/attacks/sample.json",
                "{\"attackType\":\"random_replay\"}", header("If-Match", "\"deadbeef\""));
        assertEquals(412, stale.status);

        // Correct ETag is accepted
        Response ok = http("PUT", "/api/configs/attacks/sample.json",
                "{\"attackType\":\"random_replay\"}", header("If-Match", etag));
        assertEquals(200, ok.status);

        Response after = http("GET", "/api/configs/attacks/sample.json", null, null);
        assertNotEquals(etag, after.etag);
    }

    @Test
    void deleteRemovesFile() throws IOException {
        Response del = http("DELETE", "/api/configs/attacks/sample.json", null, null);
        assertEquals(204, del.status);
        Response get = http("GET", "/api/configs/attacks/sample.json", null, null);
        assertEquals(404, get.status);
    }

    @Test
    void backupCreatedOnOverwrite() throws IOException {
        Response put = http("PUT", "/api/configs/attacks/sample.json",
                "{\"attackType\":\"random_replay\",\"enabled\":false}", null);
        assertEquals(200, put.status);
        Path backupDir = backupRoot.resolve("attacks");
        assertTrue(Files.isDirectory(backupDir), "backup dir should exist");
        try (var stream = Files.list(backupDir)) {
            assertTrue(stream.findAny().isPresent(), "at least one backup file expected");
        }
    }

    @Test
    void treeGroupsBySubfolder() throws IOException {
        Response r = http("GET", "/api/configs/tree", null, null);
        assertEquals(200, r.status);
        assertTrue(r.body.contains("\"attacks\""), r.body);
    }

    // --- helpers ---

    private static class Response {
        int status;
        String body;
        String etag;
    }

    private static String[] header(String k, String v) {
        return new String[]{k, v};
    }

    private Response http(String method, String path, String body, String[] header) throws IOException {
        URL url = URI.create("http://127.0.0.1:" + port + path).toURL();
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        if (header != null) c.setRequestProperty(header[0], header[1]);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        Response r = new Response();
        r.status = c.getResponseCode();
        r.etag = c.getHeaderField("ETag");
        var stream = (r.status >= 400) ? c.getErrorStream() : c.getInputStream();
        if (stream != null) {
            try (var s = stream) {
                r.body = new String(s.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            r.body = "";
        }
        c.disconnect();
        return r;
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(x -> { try { Files.deleteIfExists(x); } catch (IOException ignored) {} });
        }
    }
}
