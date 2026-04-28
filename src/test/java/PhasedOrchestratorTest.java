import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import br.ufu.facom.ereno.config.ActionConfigLoader;
import br.ufu.facom.ereno.parallel.PhasedParallelOrchestrator;
import br.ufu.facom.ereno.parallel.PipelineActionExecutor;

/**
 * Unit tests for the phased parallel pipeline orchestrator and the
 * {@code expand} sugar in {@link ActionConfigLoader}.
 */
public class PhasedOrchestratorTest {

    /** Capturing executor that records the order/threads of jobs. */
    private static final class RecordingExecutor implements PipelineActionExecutor {
        final List<String> labels = Collections.synchronizedList(new ArrayList<>());
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void executeActionFromConfigFile(String actionName, String configFile) {
            labels.add("file:" + actionName + ":" + configFile);
        }

        @Override
        public void executeActionWithOverrides(
                ActionConfigLoader.PipelineStep step, String variationType,
                Object currentValue, int iterationNumber, ActionConfigLoader.LoopConfig loopConfig) {
            // not used in phased path
        }

        @Override
        public void executeJob(ActionConfigLoader.PipelineStep job) throws InterruptedException {
            // Sleep briefly to maximise overlap between concurrent jobs
            Thread.sleep(20);
            String label = job.description != null ? job.description : job.action;
            labels.add(label);
            threads.add(Thread.currentThread().getName());
        }
    }

    @Test
    public void expandProducesCartesianProductFromInlineTemplate() throws Exception {
        // Compose a minimal pipeline JSON with a single phase that expands
        // 3 variants × 2 seeds into 6 jobs.
        String json = "{\n" +
                "  \"action\": \"pipeline\",\n" +
                "  \"parallelExecution\": { \"enabled\": true, \"workers\": 4, \"failFast\": true },\n" +
                "  \"phases\": [\n" +
                "    {\n" +
                "      \"name\": \"train_models\",\n" +
                "      \"expand\": {\n" +
                "        \"axes\": { \"variant\": [\"a\",\"b\",\"c\"], \"seed\": [1,2] },\n" +
                "        \"template\": {\n" +
                "          \"action\": \"train_model\",\n" +
                "          \"description\": \"variant=${variant} seed=${seed}\",\n" +
                "          \"inline\": { \"trainingDataset\": \"data/${variant}.arff\", \"randomSeed\": \"${seed}\" }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";
        File tmp = writeTempJson(json);

        ActionConfigLoader loader = new ActionConfigLoader();
        loader.load(tmp.getAbsolutePath());
        ActionConfigLoader.MainConfig cfg = loader.getMainConfig();

        assertTrue(PhasedParallelOrchestrator.isPhasedEnabled(cfg));
        assertEquals(1, cfg.phases.size());
        assertEquals(6, cfg.phases.get(0).jobs.size(), "3 variants × 2 seeds = 6 jobs");

        // Verify variable substitution actually happened in inline JSON.
        ActionConfigLoader.PipelineStep first = cfg.phases.get(0).jobs.get(0);
        assertNotNull(first.inline);
        String trainingDataset = first.inline.get("trainingDataset").getAsString();
        assertTrue(trainingDataset.startsWith("data/") && trainingDataset.endsWith(".arff"),
                "expected substituted variant in path, got: " + trainingDataset);
        assertTrue(first.description.contains("variant=") && first.description.contains("seed="),
                "expected variables substituted in description: " + first.description);
    }

    @Test
    public void executePhasedRunsAllJobsAcrossMultipleThreads() throws Exception {
        String json = "{\n" +
                "  \"action\": \"pipeline\",\n" +
                "  \"parallelExecution\": { \"enabled\": true, \"workers\": 4, \"failFast\": true },\n" +
                "  \"phases\": [\n" +
                "    {\n" +
                "      \"name\": \"phase1\",\n" +
                "      \"expand\": {\n" +
                "        \"axes\": { \"i\": [1,2,3,4,5,6,7,8] },\n" +
                "        \"template\": { \"action\": \"train_model\", \"description\": \"job-${i}\", \"inline\": { \"k\":\"v\" } }\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"phase2\",\n" +
                "      \"expand\": {\n" +
                "        \"axes\": { \"j\": [1,2,3,4] },\n" +
                "        \"template\": { \"action\": \"evaluate\", \"description\": \"eval-${j}\", \"inline\": { \"k\":\"v\" } }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";
        File tmp = writeTempJson(json);
        ActionConfigLoader loader = new ActionConfigLoader();
        loader.load(tmp.getAbsolutePath());

        RecordingExecutor exec = new RecordingExecutor();
        PhasedParallelOrchestrator.executePhasedPipeline(loader.getMainConfig(), exec);

        assertEquals(12, exec.labels.size(), "all jobs from both phases must run");
        long phase1Jobs = exec.labels.stream().filter(l -> l.startsWith("job-")).count();
        long phase2Jobs = exec.labels.stream().filter(l -> l.startsWith("eval-")).count();
        assertEquals(8, phase1Jobs);
        assertEquals(4, phase2Jobs);

        long uniqueThreads = exec.threads.stream().distinct().count();
        assertTrue(uniqueThreads >= 2,
                "expected jobs to run on at least 2 worker threads, got " + uniqueThreads);
    }

    @Test
    public void phasesAndLoopAreMutuallyExclusive() throws Exception {
        String json = "{\n" +
                "  \"action\": \"pipeline\",\n" +
                "  \"loop\": { \"variationType\": \"randomSeed\", \"values\": [1], \"steps\": [] },\n" +
                "  \"phases\": [ { \"name\": \"x\", \"jobs\": [ { \"action\": \"train_model\", \"actionConfigFile\": \"foo.json\" } ] } ]\n" +
                "}\n";
        File tmp = writeTempJson(json);
        ActionConfigLoader loader = new ActionConfigLoader();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> loader.load(tmp.getAbsolutePath()));
        assertTrue(e.getMessage().toLowerCase().contains("phases") && e.getMessage().toLowerCase().contains("loop"));
    }

    @Test
    public void barrierBetweenPhasesIsHonored() throws Exception {
        String json = "{\n" +
                "  \"action\": \"pipeline\",\n" +
                "  \"parallelExecution\": { \"enabled\": true, \"workers\": 4, \"failFast\": true },\n" +
                "  \"phases\": [\n" +
                "    { \"name\": \"A\", \"expand\": { \"axes\": { \"i\":[1,2,3,4] },\n" +
                "        \"template\": { \"action\": \"train_model\", \"description\": \"A-${i}\", \"inline\": { \"k\":\"v\" } } } },\n" +
                "    { \"name\": \"B\", \"expand\": { \"axes\": { \"j\":[1,2,3,4] },\n" +
                "        \"template\": { \"action\": \"evaluate\",  \"description\": \"B-${j}\", \"inline\": { \"k\":\"v\" } } } }\n" +
                "  ]\n" +
                "}\n";
        File tmp = writeTempJson(json);
        ActionConfigLoader loader = new ActionConfigLoader();
        loader.load(tmp.getAbsolutePath());

        // Barrier check: every B-* must appear AFTER every A-* in the recorded order.
        AtomicInteger maxPhaseAIdx = new AtomicInteger(-1);
        AtomicInteger minPhaseBIdx = new AtomicInteger(Integer.MAX_VALUE);
        RecordingExecutor exec = new RecordingExecutor();
        PhasedParallelOrchestrator.executePhasedPipeline(loader.getMainConfig(), exec);
        for (int i = 0; i < exec.labels.size(); i++) {
            String l = exec.labels.get(i);
            if (l.startsWith("A-")) maxPhaseAIdx.set(Math.max(maxPhaseAIdx.get(), i));
            if (l.startsWith("B-")) minPhaseBIdx.set(Math.min(minPhaseBIdx.get(), i));
        }
        assertTrue(maxPhaseAIdx.get() < minPhaseBIdx.get(),
                "Phase B must not start before Phase A finishes. A_max=" + maxPhaseAIdx
                        + " B_min=" + minPhaseBIdx);
    }

    @Test
    public void uc10PhasedPipelineConfigLoadsAndExpandsCorrectly() throws Exception {
        File cfg = new File("config/pipelines/pipeline_uc10_variant_evaluation_phased.json");
        assertTrue(cfg.exists(), "expected " + cfg.getAbsolutePath());

        ActionConfigLoader loader = new ActionConfigLoader();
        loader.load(cfg.getAbsolutePath());
        ActionConfigLoader.MainConfig mc = loader.getMainConfig();

        assertTrue(PhasedParallelOrchestrator.isPhasedEnabled(mc));
        assertEquals(4, mc.phases.size(), "expected 4 phases");
        assertEquals("create_training_datasets", mc.phases.get(0).name);
        assertEquals(24, mc.phases.get(0).jobs.size(), "8 variants x 3 train seeds");
        assertEquals("train_models", mc.phases.get(1).name);
        assertEquals(24, mc.phases.get(1).jobs.size());
        assertEquals("create_test_datasets", mc.phases.get(2).name);
        assertEquals(80, mc.phases.get(2).jobs.size(), "8 variants x 10 test seeds");
        assertEquals("comprehensive_evaluate", mc.phases.get(3).name);
        assertEquals(1, mc.phases.get(3).jobs.size());

        // Spot-check one expanded job: variables substituted and seed coerced to Long.
        ActionConfigLoader.PipelineStep first = mc.phases.get(0).jobs.get(0);
        assertEquals("create_attack_dataset", first.action);
        assertNotNull(first.parameterOverrides);
        assertNotNull(first.parameterOverrides.randomSeed,
                "randomSeed must be coerced from \"42\" string to Long");
        assertEquals(42L, first.parameterOverrides.randomSeed.longValue());
        assertNotNull(first.inline);
        String dir = first.inline.getAsJsonObject("output").get("directory").getAsString();
        assertTrue(dir.contains("uc10_replay_real_seed42"),
                "expected variant+seed substituted in output dir, got: " + dir);

        // Pre-phase pipeline still has create_benign.
        assertNotNull(mc.pipeline);
        assertEquals(1, mc.pipeline.size());
        assertEquals("create_benign", mc.pipeline.get(0).action);
    }

    @Test
    public void disablingParallelExecutionStillRunsAllPhasesSequentially() throws Exception {
        // Same shape as the multi-thread test but with parallelExecution.enabled=false.
        // Every job must still execute, but all on a single thread (workers=1).
        String json = "{\n" +
                "  \"action\": \"pipeline\",\n" +
                "  \"parallelExecution\": { \"enabled\": false, \"workers\": 8, \"failFast\": true },\n" +
                "  \"phases\": [\n" +
                "    { \"name\": \"A\", \"expand\": { \"axes\": { \"i\":[1,2,3,4] },\n" +
                "        \"template\": { \"action\": \"train_model\", \"description\": \"A-${i}\", \"inline\": { \"k\":\"v\" } } } },\n" +
                "    { \"name\": \"B\", \"expand\": { \"axes\": { \"j\":[1,2,3] },\n" +
                "        \"template\": { \"action\": \"evaluate\",  \"description\": \"B-${j}\", \"inline\": { \"k\":\"v\" } } } }\n" +
                "  ]\n" +
                "}\n";
        File tmp = writeTempJson(json);
        ActionConfigLoader loader = new ActionConfigLoader();
        loader.load(tmp.getAbsolutePath());

        // Phases activation must not depend on the parallelExecution flag.
        assertTrue(PhasedParallelOrchestrator.isPhasedEnabled(loader.getMainConfig()),
                "phases must run regardless of parallelExecution.enabled");

        RecordingExecutor exec = new RecordingExecutor();
        PhasedParallelOrchestrator.executePhasedPipeline(loader.getMainConfig(), exec);

        assertEquals(7, exec.labels.size(), "all jobs from both phases must run when parallel disabled");
        long uniqueThreads = exec.threads.stream().distinct().count();
        assertEquals(1, uniqueThreads,
                "with parallelExecution.enabled=false, expected jobs to run on a single worker thread; got " + uniqueThreads);
    }

    @Test
    public void phasesWithoutParallelExecutionBlockStillRun() throws Exception {
        // No parallelExecution declared at all -> still treated as sequential phased pipeline.
        String json = "{\n" +
                "  \"action\": \"pipeline\",\n" +
                "  \"phases\": [\n" +
                "    { \"name\": \"only\", \"jobs\": [\n" +
                "      { \"action\": \"train_model\", \"description\": \"x\", \"inline\": { \"k\":\"v\" } },\n" +
                "      { \"action\": \"train_model\", \"description\": \"y\", \"inline\": { \"k\":\"v\" } }\n" +
                "    ] }\n" +
                "  ]\n" +
                "}\n";
        File tmp = writeTempJson(json);
        ActionConfigLoader loader = new ActionConfigLoader();
        loader.load(tmp.getAbsolutePath());

        assertTrue(PhasedParallelOrchestrator.isPhasedEnabled(loader.getMainConfig()));
        RecordingExecutor exec = new RecordingExecutor();
        PhasedParallelOrchestrator.executePhasedPipeline(loader.getMainConfig(), exec);
        assertEquals(2, exec.labels.size());
    }

    private static File writeTempJson(String json) throws Exception {
        File tmp = Files.createTempFile("phased_test_", ".json").toFile();
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) {
            w.write(json);
        }
        return tmp;
    }
}
