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
}
