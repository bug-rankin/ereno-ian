import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import br.ufu.facom.ereno.config.ConfigLoader;

/**
 * Verifies that {@link ConfigLoader#setSeed(Long)} and {@link ConfigLoader#RNG}
 * are properly thread-isolated. Two threads with different seeds must observe
 * the same sequence as a freshly-seeded {@link Random} of their own seed —
 * proving no cross-thread interference.
 */
public class ConfigLoaderConcurrencyTest {

    @Test
    public void setSeed_isThreadIsolated() throws Exception {
        final int threadCount = 8;
        final int drawsPerThread = 1000;
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final ConcurrentHashMap<Long, int[]> observed = new ConcurrentHashMap<>();

        try {
            for (int t = 0; t < threadCount; t++) {
                final long seed = 1000L + t;
                pool.submit(() -> {
                    ConfigLoader.setSeed(seed);
                    int[] draws = new int[drawsPerThread];
                    for (int i = 0; i < drawsPerThread; i++) {
                        draws[i] = ConfigLoader.RNG.nextInt(1_000_000);
                    }
                    observed.put(seed, draws);
                });
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        // For each seed, regenerate the expected sequence and compare.
        for (int t = 0; t < threadCount; t++) {
            long seed = 1000L + t;
            Random expected = new Random(seed);
            int[] draws = observed.get(seed);
            for (int i = 0; i < drawsPerThread; i++) {
                assertEquals(expected.nextInt(1_000_000), draws[i],
                        "Mismatch on seed " + seed + " at draw " + i + " — RNG is not thread-isolated");
            }
        }
    }

    @Test
    public void getSeed_returnsPerThreadValue() throws Exception {
        final int threadCount = 4;
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final ConcurrentHashMap<Long, Long> observed = new ConcurrentHashMap<>();

        try {
            for (int t = 0; t < threadCount; t++) {
                final long seed = 9000L + t;
                pool.submit(() -> {
                    ConfigLoader.setSeed(seed);
                    // Yield a few times to encourage interleaving
                    Thread.yield();
                    observed.put(seed, ConfigLoader.getSeed());
                });
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        for (int t = 0; t < threadCount; t++) {
            long seed = 9000L + t;
            assertEquals(seed, observed.get(seed),
                    "Thread reading seed " + seed + " observed wrong value");
        }
    }
}
