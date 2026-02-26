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

public final class ParallelPipelineOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(ParallelPipelineOrchestrator.class.getName());

    private ParallelPipelineOrchestrator() {
    }

    public static boolean isParallelEnabled(ActionConfigLoader.MainConfig mainConfig) {
        return mainConfig != null
                && mainConfig.parallelExecution != null
                && mainConfig.parallelExecution.enabled;
    }

    public static void executeLoopPipeline(
            ActionConfigLoader.MainConfig mainConfig,
            PipelineActionExecutor actionExecutor) throws Exception {

        if (mainConfig == null || mainConfig.loop == null) {
            throw new IllegalArgumentException("Parallel loop pipeline requires loop configuration");
        }

        ActionConfigLoader.LoopConfig loop = mainConfig.loop;
        if (loop.values == null || loop.values.isEmpty()) {
            throw new IllegalArgumentException("Loop configuration requires 'values' array");
        }
        if (loop.steps == null || loop.steps.isEmpty()) {
            throw new IllegalArgumentException("Loop configuration requires 'steps' array");
        }

        int workers = resolveWorkers(mainConfig.parallelExecution);
        boolean failFast = resolveFailFast(mainConfig.parallelExecution);

        LOGGER.info("=== Starting Parallel Pipeline with Loop Execution ===");
        LOGGER.info(() -> "Variation type: " + loop.variationType);
        LOGGER.info(() -> "Iterations: " + loop.values.size());
        LOGGER.info(() -> "Steps per iteration: " + loop.steps.size());
        LOGGER.info(() -> "Parallel workers: " + workers);
        LOGGER.info(() -> "Fail-fast: " + failFast);

        executePreLoopSteps(mainConfig, actionExecutor);
        executeLoopIterationsInParallel(mainConfig, actionExecutor, workers, failFast);

        LOGGER.info("=== Parallel Pipeline with Loop Execution Completed Successfully ===");
    }

    private static int resolveWorkers(ActionConfigLoader.ParallelExecutionConfig cfg) {
        if (cfg == null || cfg.workers <= 0) {
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

        LOGGER.info("--- Executing Pre-Loop Steps (Sequential) ---");
        for (ActionConfigLoader.PipelineStep step : mainConfig.pipeline) {
            String stepDesc = step.description != null ? step.description : step.action;
            LOGGER.info(() -> "Pre-loop step: " + stepDesc);
            actionExecutor.executeActionFromConfigFile(step.action, step.actionConfigFile);
        }
    }

    private static void executeLoopIterationsInParallel(
            ActionConfigLoader.MainConfig mainConfig,
            PipelineActionExecutor actionExecutor,
            int workers,
            boolean failFast) throws Exception {

        ActionConfigLoader.LoopConfig loop = mainConfig.loop;
        ExecutorService executorService = Executors.newFixedThreadPool(workers);
        List<Future<Void>> futures = new ArrayList<>();

        try {
            for (int iteration = 0; iteration < loop.values.size(); iteration++) {
                final int iterationIndex = iteration;
                final int iterationNumber = iteration + 1;
                Callable<Void> task = () -> {
                    Object currentValue = loop.values.get(iterationIndex);
                    LOGGER.info(() -> "[Parallel] Iteration " + iterationNumber + " started: " + currentValue);
                    for (ActionConfigLoader.PipelineStep step : loop.steps) {
                        actionExecutor.executeActionWithOverrides(
                                step,
                                loop.variationType,
                                currentValue,
                                iterationNumber,
                                loop);
                    }
                    LOGGER.info(() -> "[Parallel] Iteration " + iterationNumber + " completed");
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
                    throw e;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    failures.add(cause);
                    LOGGER.severe("Parallel iteration failed: " + cause.getMessage());
                    if (failFast) {
                        cancelPending(futures);
                        throw new Exception("Parallel pipeline failed (fail-fast mode)", cause);
                    }
                }
            }

            if (!failures.isEmpty()) {
                throw new Exception("Parallel pipeline completed with " + failures.size() + " failed iteration(s)",
                        failures.get(0));
            }
        } finally {
            executorService.shutdownNow();
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
