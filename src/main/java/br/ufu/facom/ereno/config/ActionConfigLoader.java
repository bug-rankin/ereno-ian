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
            ConfigLoader.randomSeed = mainConfig.commonConfig.randomSeed;
            ConfigLoader.RNG = new java.util.Random(mainConfig.commonConfig.randomSeed);
            LOGGER.info(() -> "Random seed set to: " + mainConfig.commonConfig.randomSeed);
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
