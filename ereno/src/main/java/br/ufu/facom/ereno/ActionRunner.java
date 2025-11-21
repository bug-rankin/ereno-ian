package br.ufu.facom.ereno;

import br.ufu.facom.ereno.actions.CompareAction;
import br.ufu.facom.ereno.actions.CreateBenignAction;
import br.ufu.facom.ereno.actions.CreateTrainingAction;
import br.ufu.facom.ereno.actions.CreateTestAction;
import br.ufu.facom.ereno.actions.TrainAndTestAction;
import br.ufu.facom.ereno.config.ActionConfigLoader;
import br.ufu.facom.ereno.config.ConfigLoader;

import java.io.IOException;
import java.util.logging.Logger;

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
            System.err.println("  - create_training: Create training dataset from benign data and attacks");
            System.err.println("  - create_test: Create test dataset and evaluate against training data");
            System.err.println("  - train_and_test: Create both training and test datasets, then evaluate");
            System.err.println("  - compare: Compare benign data with attack dataset");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  java -jar ERENO.jar config/main_config.json");
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

                case CREATE_TRAINING:
                    LOGGER.info("Executing CREATE_TRAINING action");
                    CreateTrainingAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case CREATE_TEST:
                    LOGGER.info("Executing CREATE_TEST action");
                    CreateTestAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case TRAIN_AND_TEST:
                    LOGGER.info("Executing TRAIN_AND_TEST action");
                    TrainAndTestAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case COMPARE:
                    CompareAction.execute(actionLoader.getMainConfig().actionConfigFile);
                    break;

                case UNKNOWN:
                default:
                    LOGGER.severe("Unknown action: " + actionLoader.getMainConfig().action);
                    System.err.println("Unknown action: " + actionLoader.getMainConfig().action);
                    System.err.println("Valid actions: create_benign, create_training, create_test, train_and_test, compare");
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
}
