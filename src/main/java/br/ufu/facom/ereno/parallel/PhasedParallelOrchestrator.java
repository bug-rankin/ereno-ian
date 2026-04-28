package br.ufu.facom.ereno.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import br.ufu.facom.ereno.config.ActionConfigLoader;

/**
 * Runs a pipeline expressed as ordered <i>phases</i>. All jobs within a phase
 * are submitted to a shared thread pool and executed concurrently; phases are
 * separated by barriers (the orchestrator waits for every job in phase N to
 * finish before submitting phase N+1).
 *
 * <p>Pre-phase steps (declared via {@link ActionConfigLoader.MainConfig#pipeline})
 * run sequentially before the first phase, and are useful for one-off setup
 * (e.g. a single {@code create_benign} job whose output every phase A job
 * consumes).</p>
 *
 * <p>Activation: this orchestrator is invoked when both
 * {@code parallelExecution.enabled} is true and {@code phases} is non-empty.
 * It is mutually exclusive with the legacy single-loop orchestrator.</p>
 */
public final class PhasedParallelOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(PhasedParallelOrchestrator.class.getName());

    private PhasedParallelOrchestrator() {
    }

    /**
     * True iff the config declares one or more {@code phases}. Phases run
     * sequentially as a pipeline regardless of {@code parallelExecution.enabled};
     * the flag only controls whether the jobs <i>within</i> each phase run
     * concurrently (enabled=true) or one at a time (enabled=false).
     */
    public static boolean isPhasedEnabled(ActionConfigLoader.MainConfig mainConfig) {
        return mainConfig != null
                && mainConfig.phases != null
                && !mainConfig.phases.isEmpty();
    }

    public static void executePhasedPipeline(
            ActionConfigLoader.MainConfig mainConfig,
            PipelineActionExecutor actionExecutor) throws Exception {

        if (mainConfig == null || mainConfig.phases == null || mainConfig.phases.isEmpty()) {
            throw new IllegalArgumentException("Phased pipeline requires non-empty phases");
        }

        int workers = resolveWorkers(mainConfig.parallelExecution);
        boolean failFast = resolveFailFast(mainConfig.parallelExecution);

        LOGGER.info("=== Starting Phased Parallel Pipeline ===");
        LOGGER.info(() -> "Phases: " + mainConfig.phases.size());
        LOGGER.info(() -> "Workers: " + workers);
        LOGGER.info(() -> "Fail-fast: " + failFast);

        executePreLoopSteps(mainConfig, actionExecutor);

        ExecutorService executorService = Executors.newFixedThreadPool(workers);
        try {
            for (int phaseIdx = 0; phaseIdx < mainConfig.phases.size(); phaseIdx++) {
                ActionConfigLoader.Phase phase = mainConfig.phases.get(phaseIdx);
                String phaseName = phase.name != null ? phase.name : "phase_" + (phaseIdx + 1);
                int jobCount = phase.jobs.size();
                LOGGER.info("--- Phase '" + phaseName + "' (" + jobCount + " jobs) ---");
                long phaseStart = System.nanoTime();

                runPhase(phaseName, phase.jobs, executorService, actionExecutor, failFast);

                long elapsedMs = (System.nanoTime() - phaseStart) / 1_000_000L;
                LOGGER.info("--- Phase '" + phaseName + "' completed in " + elapsedMs + " ms ---");
            }
        } finally {
            executorService.shutdownNow();
        }

        LOGGER.info("=== Phased Parallel Pipeline Completed Successfully ===");
    }

    private static void runPhase(
            String phaseName,
            List<ActionConfigLoader.PipelineStep> jobs,
            ExecutorService executorService,
            PipelineActionExecutor actionExecutor,
            boolean failFast) throws Exception {

        List<Future<Void>> futures = new ArrayList<>(jobs.size());
        for (int i = 0; i < jobs.size(); i++) {
            final int jobNumber = i + 1;
            final int totalJobs = jobs.size();
            final ActionConfigLoader.PipelineStep job = jobs.get(i);
            Callable<Void> task = () -> {
                LOGGER.info(() -> "[" + phaseName + "] [Job " + jobNumber + "/" + totalJobs + "] started: " + jobLabel(job));
                actionExecutor.executeJob(job);
                LOGGER.info(() -> "[" + phaseName + "] [Job " + jobNumber + "/" + totalJobs + "] completed");
                return null;
            };
            futures.add(executorService.submit(task));
        }

        List<Throwable> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelPending(futures);
                throw e;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                failures.add(cause);
                LOGGER.severe("[" + phaseName + "] Job failed: " + cause.getMessage());
                if (failFast) {
                    cancelPending(futures);
                    throw new Exception("Phase '" + phaseName + "' failed (fail-fast)", cause);
                }
            }
        }

        if (!failures.isEmpty()) {
            throw new Exception("Phase '" + phaseName + "' completed with "
                    + failures.size() + " failed job(s)", failures.get(0));
        }
    }

    private static String jobLabel(ActionConfigLoader.PipelineStep job) {
        return job.action + (job.description != null ? " — " + job.description : "");
    }

    private static int resolveWorkers(ActionConfigLoader.ParallelExecutionConfig cfg) {
        // When parallelExecution is absent or disabled, run jobs within each
        // phase one at a time. The phase boundary still acts as a barrier.
        if (cfg == null || !cfg.enabled) {
            return 1;
        }
        if (cfg.workers <= 0) {
            return 16;
        }
        return cfg.workers;
    }

    private static boolean resolveFailFast(ActionConfigLoader.ParallelExecutionConfig cfg) {
        if (cfg == null) {
            return true;
        }
        return cfg.failFast;
    }

    private static void executePreLoopSteps(
            ActionConfigLoader.MainConfig mainConfig,
            PipelineActionExecutor actionExecutor) throws Exception {

        if (mainConfig.pipeline == null || mainConfig.pipeline.isEmpty()) {
            return;
        }
        LOGGER.info("--- Executing Pre-Phase Steps (Sequential) ---");
        for (ActionConfigLoader.PipelineStep step : mainConfig.pipeline) {
            String stepDesc = step.description != null ? step.description : step.action;
            LOGGER.info(() -> "Pre-phase step: " + stepDesc);
            // Pre-phase steps may be inline OR file-based.
            if (step.inline != null) {
                actionExecutor.executeJob(step);
            } else {
                actionExecutor.executeActionFromConfigFile(step.action, step.actionConfigFile);
            }
        }
    }

    private static void cancelPending(List<Future<Void>> futures) {
        for (Future<Void> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }
}
