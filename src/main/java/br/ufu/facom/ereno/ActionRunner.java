package br.ufu.facom.ereno;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import br.ufu.facom.ereno.actions.CompareAction;
import br.ufu.facom.ereno.actions.CreateAttackDatasetAction;
import br.ufu.facom.ereno.actions.CreateBenignAction;
import br.ufu.facom.ereno.actions.EvaluateAction;
import br.ufu.facom.ereno.actions.TrainModelAction;
import br.ufu.facom.ereno.config.ActionConfigLoader;
import br.ufu.facom.ereno.config.ConfigLoader;

/**
 * Main entry point for the new action-based configuration system.
 * 
 * Usage: java -jar ERENO.jar <path-to-main-config.json>
 * 
 * The main config specifies which action to perform and points to the
 * action-specific configuration file.
 */
public class ActionRunner {

    // Static initializer runs before anything else - disable Weka's dynamic properties and GUI
    static {
        System.setProperty("weka.gui.GenericPropertiesCreator.useDynamic", "false");
        System.setProperty("java.awt.headless", "true");
    }

    private static final Logger LOGGER = Logger.getLogger(ActionRunner.class.getName());

    public static void main(String[] args) {
        
        if (args.length < 1) {
            System.err.println("Usage: java -jar ERENO.jar <main-config.json>");
            System.err.println();
            System.err.println("Available actions:");
            System.err.println("  - create_benign: Generate benign (legitimate) traffic dataset");
            System.err.println("  - create_attack_dataset: Create labeled dataset from benign data and attacks");
            System.err.println("  - train_model: Train ML classifiers on attack dataset and save models");
            System.err.println("  - evaluate: Evaluate trained models against test dataset");
            System.err.println("  - compare: Compare benign data with attack dataset");
            System.err.println("  - pipeline: Execute multiple actions sequentially");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  java -jar ERENO.jar config/main_config.json");
            System.err.println("  java -jar ERENO.jar config/pipeline_train_evaluate.json");
            System.exit(1);
        }

        String mainConfigPath = args[0];

        try {
            // Initialize ConfigLoader with defaults
            ConfigLoader.load();

            // Load action-based configuration
            ActionConfigLoader actionLoader = new ActionConfigLoader();
            actionLoader.load(mainConfigPath);

            LOGGER.info("Loaded configuration for action: " + actionLoader.getCurrentAction());

            // Dispatch to appropriate action handler
            switch (actionLoader.getCurrentAction()) {
                case CREATE_BENIGN:
                    CreateBenignAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case CREATE_ATTACK_DATASET:
                    LOGGER.info("Executing CREATE_ATTACK_DATASET action");
                    CreateAttackDatasetAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case TRAIN_MODEL:
                    LOGGER.info("Executing TRAIN_MODEL action");
                    TrainModelAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case EVALUATE:
                    LOGGER.info("Executing EVALUATE action");
                    EvaluateAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case COMPARE:
                    CompareAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case PIPELINE:
                    LOGGER.info("Executing PIPELINE action");
                    if (actionLoader.getMainConfig().loop != null) {
                        executePipelineWithLoop(actionLoader);
                    } else {
                        executePipeline(actionLoader);
                    }
                    break;

                case UNKNOWN:
                default:
                    LOGGER.severe("Unknown action: " + actionLoader.getMainConfig().action);
                    System.err.println("Unknown action: " + actionLoader.getMainConfig().action);
                    System.err.println("Valid actions: create_benign, create_attack_dataset, train_model, evaluate, compare, pipeline");
                    System.exit(1);
            }

            LOGGER.info("Action completed successfully");

        } catch (IOException e) {
            LOGGER.severe("Configuration error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        } catch (Exception e) {
            LOGGER.severe("Execution error: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }

    /**
     * Execute a pipeline of actions sequentially.
     */
    private static void executePipeline(ActionConfigLoader actionLoader) throws Exception {
        ActionConfigLoader.MainConfig mainConfig = actionLoader.getMainConfig();
        
        if (mainConfig.pipeline == null || mainConfig.pipeline.isEmpty()) {
            throw new IllegalArgumentException("Pipeline action requires 'pipeline' array in config");
        }
        
        LOGGER.info("=== Starting Pipeline Execution ===");
        LOGGER.info("Pipeline contains " + mainConfig.pipeline.size() + " steps");
        
        for (int i = 0; i < mainConfig.pipeline.size(); i++) {
            ActionConfigLoader.PipelineStep step = mainConfig.pipeline.get(i);
            
            LOGGER.info("\n--- Pipeline Step " + (i + 1) + "/" + mainConfig.pipeline.size() + " ---");
            LOGGER.info("Action: " + step.action);
            if (step.description != null && !step.description.isEmpty()) {
                LOGGER.info("Description: " + step.description);
            }
            
            // Create a temporary ActionConfigLoader for this step
            // For pipeline steps, we execute actions directly with their config files
            executeActionFromConfigFile(step.action, step.actionConfigFile);
            
            LOGGER.info("Step " + (i + 1) + " completed successfully");
        }
        
        LOGGER.info("\n=== Pipeline Execution Completed Successfully ===");
    }
    
    /**
     * Execute a pipeline with loop support for dataset variations.
     */
    private static void executePipelineWithLoop(ActionConfigLoader actionLoader) throws Exception {
        ActionConfigLoader.MainConfig mainConfig = actionLoader.getMainConfig();
        ActionConfigLoader.LoopConfig loop = mainConfig.loop;
        
        if (loop == null || loop.values == null || loop.values.isEmpty()) {
            throw new IllegalArgumentException("Loop configuration requires 'values' array");
        }
        
        if (loop.steps == null || loop.steps.isEmpty()) {
            throw new IllegalArgumentException("Loop configuration requires 'steps' array");
        }
        
        LOGGER.info("=== Starting Pipeline with Loop Execution ===");
        LOGGER.info("Loop variation type: " + loop.variationType);
        LOGGER.info("Number of iterations: " + loop.values.size());
        LOGGER.info("Steps per iteration: " + loop.steps.size());
        
        // Execute pre-loop pipeline steps if any
        if (mainConfig.pipeline != null && !mainConfig.pipeline.isEmpty()) {
            LOGGER.info("\n--- Executing Pre-Loop Steps ---");
            for (int i = 0; i < mainConfig.pipeline.size(); i++) {
                ActionConfigLoader.PipelineStep step = mainConfig.pipeline.get(i);
                LOGGER.info("Pre-Loop Step " + (i + 1) + "/" + mainConfig.pipeline.size() + ": " + step.action);
                executeActionFromConfigFile(step.action, step.actionConfigFile);
                LOGGER.info("Pre-Loop Step " + (i + 1) + " completed successfully");
            }
        }
        
        // Execute loop iterations
        for (int iteration = 0; iteration < loop.values.size(); iteration++) {
            Object currentValue = loop.values.get(iteration);
            
            LOGGER.info("\n========================================");
            LOGGER.info("=== Loop Iteration " + (iteration + 1) + "/" + loop.values.size() + " ===");
            LOGGER.info("Current value: " + currentValue);
            LOGGER.info("========================================");
            
            // Execute each step in the loop with parameter overrides
            for (int stepIdx = 0; stepIdx < loop.steps.size(); stepIdx++) {
                ActionConfigLoader.PipelineStep step = loop.steps.get(stepIdx);
                
                LOGGER.info("\n--- Loop Step " + (stepIdx + 1) + "/" + loop.steps.size() + " ---");
                LOGGER.info("Action: " + step.action);
                if (step.description != null && !step.description.isEmpty()) {
                    LOGGER.info("Description: " + step.description);
                }
                
                // Apply parameter overrides based on variation type and iteration
                executeActionWithOverrides(step, loop.variationType, currentValue, iteration + 1, loop);
                
                LOGGER.info("Loop Step " + (stepIdx + 1) + " completed successfully");
            }
        }
        
        LOGGER.info("\n=== Pipeline with Loop Execution Completed Successfully ===");
        LOGGER.info("Total iterations: " + loop.values.size());
    }
    
    /**
     * Execute an action with parameter overrides for loop iterations.
     */
    private static void executeActionWithOverrides(
            ActionConfigLoader.PipelineStep step,
            String variationType,
            Object currentValue,
            int iterationNumber,
            ActionConfigLoader.LoopConfig loopConfig) throws Exception {
        
        // Load the base config
        Gson gson = new Gson();
        JsonObject configJson;
        try (FileReader reader = new FileReader(step.actionConfigFile)) {
            configJson = gson.fromJson(reader, JsonObject.class);
        }
        
        // Apply overrides based on variation type
        switch (variationType != null ? variationType.toLowerCase() : "") {
            case "randomseed":
                applyRandomSeedOverride(configJson, currentValue, iterationNumber);
                break;
            case "attacksegments":
                applyAttackSegmentsOverride(configJson, currentValue, iterationNumber);
                break;
            case "parameters":
                applyCustomParametersOverride(configJson, currentValue, iterationNumber);
                break;
            default:
                LOGGER.warning("Unknown variation type: " + variationType + ", using base config");
        }
        
        // Apply step-specific overrides if present
        if (step.parameterOverrides != null) {
            applyStepOverrides(configJson, step.parameterOverrides, iterationNumber, loopConfig);
        }
        
        // Write modified config to temporary file
        String tempConfigPath = createTempConfigFile(configJson, step.action, iterationNumber);
        
        try {
            // Execute action with modified config
            executeActionFromConfigFile(step.action, tempConfigPath);
        } finally {
            // Clean up temporary config file
            new File(tempConfigPath).delete();
        }
    }
    
    /**
     * Apply random seed override to config.
     */
    private static void applyRandomSeedOverride(JsonObject config, Object value, int iteration) {
        Long seed = convertToLong(value);
        if (seed != null) {
            config.addProperty("randomSeed", seed);
            LOGGER.info("Applied randomSeed override: " + seed);
            
            // Also update ConfigLoader for attack generation
            ConfigLoader.randomSeed = seed;
            ConfigLoader.RNG = new java.util.Random(seed);
        }
    }
    
    /**
     * Apply attack segments override to config.
     */
    private static void applyAttackSegmentsOverride(JsonObject config, Object value, int iteration) {
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> enabledAttacks = (List<String>) value;
            
            if (config.has("attackSegments") && config.get("attackSegments").isJsonArray()) {
                JsonArray segments = config.getAsJsonArray("attackSegments");
                
                // Disable all segments first
                for (int i = 0; i < segments.size(); i++) {
                    JsonObject segment = segments.get(i).getAsJsonObject();
                    segment.addProperty("enabled", false);
                }
                
                // Enable only specified segments
                for (String attackName : enabledAttacks) {
                    for (int i = 0; i < segments.size(); i++) {
                        JsonObject segment = segments.get(i).getAsJsonObject();
                        if (segment.has("name") && segment.get("name").getAsString().contains(attackName)) {
                            segment.addProperty("enabled", true);
                        }
                    }
                }
                
                LOGGER.info("Applied attackSegments override: " + enabledAttacks);
            }
        }
    }
    
    /**
     * Apply custom parameters override to config.
     */
    private static void applyCustomParametersOverride(JsonObject config, Object value, int iteration) {
        if (value instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> params = (java.util.Map<String, Object>) value;
            
            for (java.util.Map.Entry<String, Object> entry : params.entrySet()) {
                String[] path = entry.getKey().split("\\.");
                JsonObject current = config;
                
                // Navigate to nested object
                for (int i = 0; i < path.length - 1; i++) {
                    if (!current.has(path[i])) {
                        current.add(path[i], new JsonObject());
                    }
                    current = current.getAsJsonObject(path[i]);
                }
                
                // Set the value
                String lastKey = path[path.length - 1];
                Object val = entry.getValue();
                if (val instanceof Number) {
                    current.addProperty(lastKey, (Number) val);
                } else if (val instanceof Boolean) {
                    current.addProperty(lastKey, (Boolean) val);
                } else {
                    current.addProperty(lastKey, val.toString());
                }
            }
            
            LOGGER.info("Applied custom parameters override: " + params.keySet());
        }
    }
    
    /**
     * Apply step-specific overrides (output paths, baseline dataset for evaluation).
     */
    private static void applyStepOverrides(
            JsonObject config, 
            ActionConfigLoader.ParameterOverrides overrides,
            int iteration,
            ActionConfigLoader.LoopConfig loopConfig) {
        
        // Apply random seed if specified
        if (overrides.randomSeed != null) {
            config.addProperty("randomSeed", overrides.randomSeed);
        }
        
        // Apply output overrides with iteration number
        if (overrides.output != null) {
            if (!config.has("output")) {
                config.add("output", new JsonObject());
            }
            JsonObject output = config.getAsJsonObject("output");
            
            if (overrides.output.directory != null) {
                output.addProperty("directory", overrides.output.directory);
            }
            
            if (overrides.output.filename != null) {
                // Replace ${iteration} placeholder
                String filename = overrides.output.filename.replace("${iteration}", String.valueOf(iteration));
                output.addProperty("filename", filename);
            }
        }
        
        // Update input paths for train_model and evaluate actions
        if (config.has("action")) {
            String action = config.get("action").getAsString();
            
            // For train_model, update training dataset path to point to the loop-generated dataset
            if (action.equals("train_model") && overrides.output != null) {
                if (!config.has("input")) {
                    config.add("input", new JsonObject());
                }
                JsonObject input = config.getAsJsonObject("input");
                
                // Construct path to the training dataset created in this iteration
                String prevOutputDir = overrides.output.directory != null ? 
                    overrides.output.directory.replace("models_variations", "training_variations") : 
                    "target/training_variations";
                String prevFilename = overrides.output.filename != null ?
                    overrides.output.filename.replace("training_metadata", "training_dataset")
                        .replace("${iteration}", String.valueOf(iteration))
                        .replace(".json", ".arff") :
                    "training_dataset_" + iteration + ".arff";
                
                // Update to use the dataset from this iteration
                String trainingPath = prevOutputDir.replace("models_variations/seed_" + iteration, "../training_variations")
                    .replace("models_variations/attacks_" + iteration, "../training_variations")
                    .replace("models_variations/params_" + iteration, "../training_variations");
                if (!trainingPath.contains("training_variations")) {
                    trainingPath = prevOutputDir.substring(0, prevOutputDir.lastIndexOf('/')) + "/training_variations";
                }
                input.addProperty("trainingDatasetPath", trainingPath + "/" + prevFilename);
                LOGGER.info("Updated training dataset path: " + trainingPath + "/" + prevFilename);
            }
            
            // For evaluate, update test dataset and model paths
            if (action.equals("evaluate")) {
                if (!config.has("input")) {
                    config.add("input", new JsonObject());
                }
                JsonObject input = config.getAsJsonObject("input");
                
                // Use baseline dataset for evaluation if specified
                if (loopConfig.baselineDataset != null) {
                    input.addProperty("testDatasetPath", loopConfig.baselineDataset);
                    LOGGER.info("Using baseline test dataset for evaluation: " + loopConfig.baselineDataset);
                }
                
                // Update model paths to point to models trained in this iteration
                if (overrides.output != null && input.has("models")) {
                    JsonArray models = input.getAsJsonArray("models");
                    String modelDir = overrides.output.directory.replace("evaluation_variations", "models_variations");
                    
                    for (int i = 0; i < models.size(); i++) {
                        JsonObject model = models.get(i).getAsJsonObject();
                        if (model.has("name")) {
                            String modelName = model.get("name").getAsString();
                            model.addProperty("modelPath", modelDir + "/" + modelName + "_model.model");
                        }
                    }
                    LOGGER.info("Updated model paths to: " + modelDir);
                }
            }
        }
    }
    
    /**
     * Create temporary config file with overrides.
     */
    private static String createTempConfigFile(JsonObject config, String actionName, int iteration) throws IOException {
        String tempDir = "target/temp_configs";
        new File(tempDir).mkdirs();
        
        String tempPath = tempDir + "/" + actionName + "_iter" + iteration + "_" + System.currentTimeMillis() + ".json";
        
        try (FileWriter writer = new FileWriter(tempPath)) {
            Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            gson.toJson(config, writer);
        }
        
        LOGGER.fine("Created temporary config: " + tempPath);
        return tempPath;
    }
    
    /**
     * Convert object to Long (handles Integer, Long, String).
     */
    private static Long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Execute a single action directly from its config file.
     */
    private static void executeActionFromConfigFile(String actionName, String configFile) throws Exception {
        ActionConfigLoader.Action action = parseActionName(actionName);
        
        switch (action) {
            case CREATE_BENIGN:
                CreateBenignAction.execute(configFile);
                break;
            case CREATE_ATTACK_DATASET:
                CreateAttackDatasetAction.execute(configFile);
                break;
            case TRAIN_MODEL:
                TrainModelAction.execute(configFile);
                break;
            case EVALUATE:
                EvaluateAction.execute(configFile);
                break;
            case COMPARE:
                CompareAction.execute(configFile);
                break;
            default:
                throw new IllegalArgumentException("Unknown action in pipeline: " + actionName);
        }
    }
    
    /**
     * Parse action name string to Action enum.
     */
    private static ActionConfigLoader.Action parseActionName(String actionStr) {
        if (actionStr == null) return ActionConfigLoader.Action.UNKNOWN;
        
        switch (actionStr.toLowerCase().replace("_", "")) {
            case "createbenign":
                return ActionConfigLoader.Action.CREATE_BENIGN;
            case "createattackdataset":
            case "createtraining":
                return ActionConfigLoader.Action.CREATE_ATTACK_DATASET;
            case "trainmodel":
                return ActionConfigLoader.Action.TRAIN_MODEL;
            case "evaluate":
                return ActionConfigLoader.Action.EVALUATE;
            case "compare":
                return ActionConfigLoader.Action.COMPARE;
            default:
                return ActionConfigLoader.Action.UNKNOWN;
        }
    }
}
