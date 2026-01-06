import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import br.ufu.facom.ereno.config.ConfigLoader;

public class ConfigLoaderTest {

    @Test
    public void load_sanitizesMissingRanges() throws IOException {
        // Create a minimal JSON with empty attacksParams
        String json = "{ \"attacksParams\": {} }";
        Path tmp = Files.createTempFile("cfgtest", ".json");
        Files.write(tmp, json.getBytes());

        // Load the config from this temp file
        ConfigLoader.load(tmp.toAbsolutePath().toString());

        // After load, sanitized defaults should be present
        assertNotNull(ConfigLoader.attacksParams.uc02);
        assertTrue(ConfigLoader.attacksParams.uc02.timeTakenMinMs.minMs > 0);
        assertTrue(ConfigLoader.attacksParams.uc02.timeTakenMinMs.maxMs > ConfigLoader.attacksParams.uc02.timeTakenMinMs.minMs);

        assertNotNull(ConfigLoader.attacksParams.uc05);
        assertTrue(ConfigLoader.attacksParams.uc05.stNumMin.min > 0);
        assertTrue(ConfigLoader.attacksParams.uc05.stNumMax.max >= ConfigLoader.attacksParams.uc05.stNumMin.min);

        // Clean up
        Files.deleteIfExists(tmp);
    }
}
