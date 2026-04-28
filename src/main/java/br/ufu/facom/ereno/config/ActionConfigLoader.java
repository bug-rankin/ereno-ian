package br.ufu.facom.ereno.config;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * New configuration system that supports action-based configuration files.
 * Main config specifies the action type and points to action-specific config.
 */
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
        // For pipeline support
        public List<PipelineStep> pipeline;
        // For loop support
        public LoopConfig loop;
        // Optional parallel execution settings (sequential by default)
        public ParallelExecutionConfig parallelExecution;
        // Optional phased parallel execution (jobs within a phase run concurrently;
        // phases are separated by barriers). Mutually exclusive with `loop`.
        public List<Phase> phases;
    }

    /**
     * A group of independent jobs that run concurrently when
     * {@code parallelExecution.enabled} is true. Phases execute in order
     * with a barrier between them.
     */
    public static class Phase {
        public String name;
        // Explicit job list. May be authored directly OR populated by
        // expanding the {@link #expand} block at load time.
        public List<PipelineStep> jobs;
        // Optional sugar: cartesian product of named axes applied to a
        // template step. Substituted variables use ${axisName} syntax.
        public Expansion expand;
    }

    /**
     * Cartesian-product expansion of a template step. Each axis is a
     * named list of values; one job is generated per combination.
     *
     * <p>The template is parsed as a raw {@link JsonObject} so that
     * <code>${var}</code> placeholders may appear inside fields that are
     * statically typed (e.g. {@code parameterOverrides.randomSeed: Long}).
     * Substitution runs on the raw JSON, and Gson then coerces the resulting
     * strings into the target types when deserializing into a {@link PipelineStep}.</p>
     */
    public static class Expansion {
        // Map of axis name -> list of values (e.g. {"variant": ["a","b"], "seed": [42,43]}).
        public java.util.Map<String, List<Object>> axes;
        // Template step as raw JSON. Rendered with ${axisName} placeholders
        // for each combination, then deserialized to PipelineStep.
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
        // For inline configuration support
        public JsonObject inline;
        // For nested loop support
        public LoopConfig loop;
        // For loop overrides
        public ParameterOverrides parameterOverrides;
    }

    public static class LoopConfig {
        public String variationType; // "randomSeed", "attackSegments", "parameters", "dualAttackCombinations"
        public List<Object> values; // List of values to iterate over
        public List<PipelineStep> steps; // Steps to repeat for each iteration
        public String baselineDataset; // Path to baseline test dataset for evaluation
        // For dual attack combinations
        public List<Object> datasetPatterns; // List of pattern configurations
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

    /**
     * Loads the main configuration file and the action-specific configuration.
     */
    public void load(String mainConfigPath) throws IOException {
        LOGGER.info(() -> "Loading main configuration from: " + mainConfigPath);
        
        // Load main config
        try (FileReader reader = new FileReader(mainConfigPath)) {
            Gson gson = new Gson();
            mainConfig = gson.fromJson(reader, MainConfig.class);
        }

        if (mainConfig == null || mainConfig.action == null) {
            throw new IOException("Main config is invalid or missing action field");
        }

        // Parse action type
        currentAction = parseAction(mainConfig.action);
        LOGGER.info(() -> "Action type: " + currentAction);

        // Load action-specific config (skip for pipeline actions)
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

        // Initialize RNG if seed is provided
        if (mainConfig.commonConfig != null && mainConfig.commonConfig.randomSeed != null) {
            ConfigLoader.setSeed(mainConfig.commonConfig.randomSeed);
            LOGGER.info(() -> "Random seed set to: " + mainConfig.commonConfig.randomSeed);
        }

        // Expand any phase.expand sugar into explicit jobs.
        expandPhases();
        validatePhasesAndLoop();
    }

    /**
     * If a phase has an {@code expand} block, take the cartesian product of
     * its axes, render the template via {@link VariableSubstitutor}, and
     * append the resulting jobs to {@code phase.jobs}.
     */
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
                // Substitute on the raw JSON object first, then deserialize.
                // This lets ${var} placeholders appear inside statically typed
                // fields (e.g. parameterOverrides.randomSeed: Long).
                JsonObject rendered = br.ufu.facom.ereno.utils.VariableSubstitutor.substitute(ex.template, vars);
                PipelineStep cloned = gson.fromJson(rendered, PipelineStep.class);
                phase.jobs.add(cloned);
            }
            LOGGER.info(() -> "Phase '" + phase.name + "' expanded to " + phase.jobs.size() + " jobs");
        }
    }

    /**
     * Cartesian product over a map of axis -> values. Each output entry is a
     * variable map suitable for {@link VariableSubstitutor#substitute}.
     */
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

    /**
     * Render an axis value as a string for placeholder substitution. Gson
     * deserializes {@code List<Object>} numbers as {@code Double}; for
     * whole numbers we strip the trailing ".0" so that values like 42 don't
     * become "42.0" (which then fails to coerce back into Long fields).
     */
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

    /** Reject configs that mix phases with loop-style execution. */
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

    /**
     * Parse action string to Action enum.
     */
    private Action parseAction(String actionStr) {
        if (actionStr == null) return Action.UNKNOWN;
        
        switch (actionStr.toLowerCase().replace("_", "")) {
            case "createbenign":
                return Action.CREATE_BENIGN;
            case "createattackdataset":
            case "createtraining": // backwards compatibility
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

    /**
     * Verify that a file exists and is readable.
     */
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

    /**
     * Get a typed object from the action config.
     */
    public <T> T getActionConfigAs(Class<T> clazz) {
        Gson gson = new Gson();
        return gson.fromJson(actionConfig, clazz);
    }
}
