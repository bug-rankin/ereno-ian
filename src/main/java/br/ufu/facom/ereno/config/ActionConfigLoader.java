package br.ufu.facom.ereno.config;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class ActionConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(ActionConfigLoader.class.getName());

    public enum Action {
        CREATE_BENIGN,
        CREATE_ATTACK_DATASET,
        TRAIN_MODEL,
        EVALUATE,
        COMPREHENSIVE_EVALUATE,
        COMPARE,
        PIPELINE,
        UNKNOWN
    }

    public static class MainConfig {
        public String action;
        public String actionConfigFile;
        public CommonConfig commonConfig;

        public List<PipelineStep> pipeline;

        public LoopConfig loop;

        public ParallelExecutionConfig parallelExecution;

        public List<Phase> phases;
    }

    public static class Phase {
        public String name;

        public List<PipelineStep> jobs;

        public Expansion expand;
    }

    public static class Expansion {

        public java.util.Map<String, List<Object>> axes;

        public JsonObject template;
    }

    public static class ParallelExecutionConfig {
        public boolean enabled = false;
        public int workers = 16;
        public boolean failFast = true;
    }

    public static class PipelineStep {
        public String action;
        public String actionConfigFile;
        public String description;

        public JsonObject inline;

        public LoopConfig loop;

        public ParameterOverrides parameterOverrides;
    }

    public static class LoopConfig {
        public String variationType;
        public List<Object> values;
        public List<PipelineStep> steps;
        public String baselineDataset;

        public List<Object> datasetPatterns;
    }

    public static class ParameterOverrides {
        public Long randomSeed;
        public OutputOverrides output;
        public List<String> enabledAttackSegments;
        public java.util.Map<String, Object> customParameters;
    }

    public static class OutputOverrides {
        public String directory;
        public String filename;
    }

    public static class CommonConfig {
        public Long randomSeed;
        public String outputFormat = "arff";
    }

    private MainConfig mainConfig;
    private JsonObject actionConfig;
    private Action currentAction;

    public void load(String mainConfigPath) throws IOException {
        LOGGER.info(() -> "Loading main configuration from: " + mainConfigPath);

        try (FileReader reader = new FileReader(mainConfigPath)) {
            Gson gson = new Gson();
            mainConfig = gson.fromJson(reader, MainConfig.class);
        }

        if (mainConfig == null || mainConfig.action == null) {
            throw new IOException("Main config is invalid or missing action field");
        }

        currentAction = parseAction(mainConfig.action);
        LOGGER.info(() -> "Action type: " + currentAction);

        if (currentAction != Action.PIPELINE) {
            if (mainConfig.actionConfigFile != null && !mainConfig.actionConfigFile.trim().isEmpty()) {
                LOGGER.info(() -> "Loading action config from: " + mainConfig.actionConfigFile);
                try (FileReader reader = new FileReader(mainConfig.actionConfigFile)) {
                    Gson gson = new Gson();
                    actionConfig = gson.fromJson(reader, JsonObject.class);
                }
            } else {
                throw new IOException("Action config file path is required in main config");
            }
        }

        if (mainConfig.commonConfig != null && mainConfig.commonConfig.randomSeed != null) {
            ConfigLoader.setSeed(mainConfig.commonConfig.randomSeed);
            LOGGER.info(() -> "Random seed set to: " + mainConfig.commonConfig.randomSeed);
        }

        expandPhases();
        validatePhasesAndLoop();
    }

    private void expandPhases() {
        if (mainConfig == null || mainConfig.phases == null) return;
        for (Phase phase : mainConfig.phases) {
            if (phase == null || phase.expand == null) continue;
            Expansion ex = phase.expand;
            if (ex.template == null) {
                throw new IllegalArgumentException(
                        "Phase '" + phase.name + "' has expand without template");
            }
            if (ex.axes == null || ex.axes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Phase '" + phase.name + "' expand must declare at least one axis");
            }
            if (phase.jobs == null) {
                phase.jobs = new java.util.ArrayList<>();
            }
            List<java.util.Map<String, String>> combos = cartesianProduct(ex.axes);
            Gson gson = new Gson();
            for (java.util.Map<String, String> vars : combos) {

                JsonObject rendered = br.ufu.facom.ereno.util.VariableSubstitutor.substitute(ex.template, vars);
                PipelineStep cloned = gson.fromJson(rendered, PipelineStep.class);
                phase.jobs.add(cloned);
            }
            LOGGER.info(() -> "Phase '" + phase.name + "' expanded to " + phase.jobs.size() + " jobs");
        }
    }

    private static List<java.util.Map<String, String>> cartesianProduct(
            java.util.Map<String, List<Object>> axes) {
        List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        result.add(new java.util.LinkedHashMap<>());
        for (java.util.Map.Entry<String, List<Object>> entry : axes.entrySet()) {
            String axis = entry.getKey();
            List<Object> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("Axis '" + axis + "' has no values");
            }
            List<java.util.Map<String, String>> next = new java.util.ArrayList<>();
            for (java.util.Map<String, String> partial : result) {
                for (Object v : values) {
                    java.util.Map<String, String> copy = new java.util.LinkedHashMap<>(partial);
                    copy.put(axis, formatAxisValue(v));
                    next.add(copy);
                }
            }
            result = next;
        }
        return result;
    }

    private static String formatAxisValue(Object v) {
        if (v instanceof Double) {
            double d = (Double) v;
            if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.floor(d)) {
                return Long.toString((long) d);
            }
        }
        if (v instanceof Float) {
            float f = (Float) v;
            if (!Float.isInfinite(f) && !Float.isNaN(f) && f == Math.floor(f)) {
                return Long.toString((long) f);
            }
        }
        return String.valueOf(v);
    }

    private void validatePhasesAndLoop() {
        if (mainConfig == null) return;
        boolean hasPhases = mainConfig.phases != null && !mainConfig.phases.isEmpty();
        boolean hasLoop = mainConfig.loop != null;
        if (hasPhases && hasLoop) {
            throw new IllegalArgumentException(
                    "Pipeline config cannot declare both 'phases' and 'loop'. Use one or the other.");
        }
        if (hasPhases) {
            for (Phase phase : mainConfig.phases) {
                if (phase.jobs == null || phase.jobs.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Phase '" + phase.name + "' has no jobs (after expansion).");
                }
                for (PipelineStep job : phase.jobs) {
                    if (job.action == null || job.action.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "Phase '" + phase.name + "' contains a job without 'action'");
                    }
                    if (job.inline == null && (job.actionConfigFile == null || job.actionConfigFile.trim().isEmpty())) {
                        throw new IllegalArgumentException(
                                "Phase '" + phase.name + "' job '" + job.action
                                        + "' must declare 'inline' or 'actionConfigFile'");
                    }
                }
            }
        }
    }

    private Action parseAction(String actionStr) {
        if (actionStr == null) return Action.UNKNOWN;

        switch (actionStr.toLowerCase().replace("_", "")) {
            case "createbenign":
                return Action.CREATE_BENIGN;
            case "createattackdataset":
            case "createtraining":
                return Action.CREATE_ATTACK_DATASET;
            case "trainmodel":
                return Action.TRAIN_MODEL;
            case "evaluate":
                return Action.EVALUATE;
            case "comprehensiveevaluate":
                return Action.COMPREHENSIVE_EVALUATE;
            case "compare":
                return Action.COMPARE;
            case "pipeline":
                return Action.PIPELINE;
            default:
                return Action.UNKNOWN;
        }
    }

    public static boolean verifyFile(String path, String description) {
        if (path == null || path.trim().isEmpty()) {
            LOGGER.warning(() -> description + " path is null or empty");
            return false;
        }

        if (!Files.exists(Paths.get(path))) {
            LOGGER.severe(() -> description + " file not found: " + path);
            return false;
        }

        if (!Files.isReadable(Paths.get(path))) {
            LOGGER.severe(() -> description + " file is not readable: " + path);
            return false;
        }

        LOGGER.info(() -> description + " file verified: " + path);
        return true;
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public JsonObject getActionConfig() {
        return actionConfig;
    }

    public Action getCurrentAction() {
        return currentAction;
    }

    public <T> T getActionConfigAs(Class<T> clazz) {
        Gson gson = new Gson();
        return gson.fromJson(actionConfig, clazz);
    }
}
