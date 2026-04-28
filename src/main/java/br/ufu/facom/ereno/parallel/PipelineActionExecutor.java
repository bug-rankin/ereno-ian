package br.ufu.facom.ereno.parallel;

import br.ufu.facom.ereno.config.ActionConfigLoader;

public interface PipelineActionExecutor {
    void executeActionFromConfigFile(String actionName, String configFile) throws Exception;

    void executeActionWithOverrides(
            ActionConfigLoader.PipelineStep step,
            String variationType,
            Object currentValue,
            int iterationNumber,
            ActionConfigLoader.LoopConfig loopConfig) throws Exception;

    /**
     * Execute a single, self-contained pipeline step (used by the phased
     * orchestrator). The job carries its own action + (inline or
     * actionConfigFile) and may set a per-thread seed via
     * {@code parameterOverrides.randomSeed}.
     *
     * <p>Default implementation throws; concrete executors that want to be
     * driven by {@link PhasedParallelOrchestrator} must override this.</p>
     */
    default void executeJob(ActionConfigLoader.PipelineStep job) throws Exception {
        throw new UnsupportedOperationException(
                "executeJob not implemented by this PipelineActionExecutor");
    }
}

