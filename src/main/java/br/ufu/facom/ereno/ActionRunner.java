package br.ufu.facom.ereno;

import java.io.IOException;
import java.util.logging.Logger;

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
                    executePipeline(actionLoader);
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
