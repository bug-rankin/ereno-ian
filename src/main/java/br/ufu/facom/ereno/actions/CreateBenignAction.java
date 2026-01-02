package br.ufu.facom.ereno.actions;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.google.gson.Gson;

import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.tracking.ExperimentTracker;
import br.ufu.facom.ereno.util.BenignDataManager;

/**
 * Action runner for creating benign datasets.
 */
public class CreateBenignAction {

    private static final Logger LOGGER = Logger.getLogger(CreateBenignAction.class.getName());

    public static class Config {
        public String action;
        public String description;
        public OutputConfig output;
        public GenerationConfig generation;
        public GooseFlowConfig gooseFlow;
        public SetupIEDConfig setupIED;

        public static class OutputConfig {
            public String directory = "target/benign_data";
            public String[] formats = {"arff"};
            public String filenamePrefix;
            public boolean enableTracking = true;
            public String experimentId; // Optional: link to existing experiment
        }

        public static class GenerationConfig {
            public int numberOfMessages = 1000;
            public int faultProbability = 5;
        }

        public static class GooseFlowConfig {
            public String goID;
            public String ethSrc;
            public String ethDst;
            public String ethType;
            public String gooseAppid;
            public String TPID;
            public boolean ndsCom;
            public boolean test;
            public boolean cbstatus;
        }

        public static class SetupIEDConfig {
            public String iedName;
            public String gocbRef;
            public String datSet;
            public String minTime;
            public String maxTime;
            public String timestamp;
            public String stNum;
            public String sqNum;
        }
    }

    public static void execute(String configPath) throws IOException {
        LOGGER.info("=== Starting Create Benign Data Action ===");

        // Load and parse action config
        try (java.io.FileReader reader = new java.io.FileReader(configPath)) {
            Gson gson = new Gson();
            Config config = gson.fromJson(reader, Config.class);

        // Populate ConfigLoader for compatibility with existing code
        populateConfigLoader(config);

        // Create output directory
        File outDir = new File(config.output.directory);
        if (!outDir.exists()) {
            outDir.mkdirs();
            LOGGER.info("Created output directory: " + config.output.directory);
        }

        // Generate benign data
        LOGGER.info("Generating " + config.generation.numberOfMessages + " benign messages...");
        LegitimateProtectionIED ied = new LegitimateProtectionIED();
        ied.run(config.generation.numberOfMessages);
        LOGGER.info("Generated " + ied.getNumberOfMessages() + " benign messages");

        // Set fault probability for filename
        ConfigLoader.benignData.faultProbability = config.generation.faultProbability;
        ConfigLoader.benignData.benignDataDir = config.output.directory;

        // Save in requested formats
        for (String format : config.output.formats) {
            LOGGER.info("Saving benign data in " + format.toUpperCase() + " format...");
            BenignDataManager.saveBenignData(ied.copyMessages(), format);
            String savedPath = BenignDataManager.getBenignDataPath(format);
            LOGGER.info("Saved to: " + savedPath);
            
            // Track dataset creation
            if (config.output.enableTracking) {
                trackBenignDataset(config, savedPath, ied.getNumberOfMessages(), configPath);
            }
        }

            LOGGER.info("=== Create Benign Data Action Completed Successfully ===");
        }
    }
    
    private static void trackBenignDataset(Config config, String savedPath, 
                                           int numMessages, String configPath) {
        try {
            ExperimentTracker tracker = new ExperimentTracker();
            
            // Determine experiment ID
            String experimentId = config.output.experimentId;
            if (experimentId == null || experimentId.isEmpty()) {
                // Create a new standalone experiment
                experimentId = tracker.startExperiment(
                    "benign_dataset_creation",
                    "Create benign dataset with " + numMessages + " messages",
                    configPath,
                    "Standalone benign dataset creation"
                );
            }
            
            // Track the dataset
            String datasetId = tracker.trackBenignDataset(
                experimentId,
                savedPath,
                configPath,
                numMessages,
                "Benign dataset, fault probability: " + config.generation.faultProbability + "%"
            );
            
            LOGGER.info("Benign dataset tracked with ID: " + datasetId);
            
            // Complete experiment if we created it
            if (config.output.experimentId == null || config.output.experimentId.isEmpty()) {
                tracker.completeExperiment(experimentId);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Failed to track benign dataset: " + e.getMessage());
            // Don't fail the action if tracking fails
        }
    }

    private static void populateConfigLoader(Config config) {
        // Populate gooseFlow
        if (config.gooseFlow != null) {
            ConfigLoader.gooseFlow.goID = config.gooseFlow.goID;
            ConfigLoader.gooseFlow.ethSrc = config.gooseFlow.ethSrc;
            ConfigLoader.gooseFlow.ethDst = config.gooseFlow.ethDst;
            ConfigLoader.gooseFlow.ethType = config.gooseFlow.ethType;
            ConfigLoader.gooseFlow.gooseAppid = config.gooseFlow.gooseAppid;
            ConfigLoader.gooseFlow.TPID = config.gooseFlow.TPID;
            ConfigLoader.gooseFlow.ndsCom = config.gooseFlow.ndsCom;
            ConfigLoader.gooseFlow.test = config.gooseFlow.test;
            ConfigLoader.gooseFlow.cbstatus = config.gooseFlow.cbstatus;
        }

        // Populate setupIED
        if (config.setupIED != null) {
            ConfigLoader.setupIED.iedName = config.setupIED.iedName;
            ConfigLoader.setupIED.gocbRef = config.setupIED.gocbRef;
            ConfigLoader.setupIED.datSet = config.setupIED.datSet;
            ConfigLoader.setupIED.minTime = config.setupIED.minTime;
            ConfigLoader.setupIED.maxTime = config.setupIED.maxTime;
            ConfigLoader.setupIED.timestamp = config.setupIED.timestamp;
            ConfigLoader.setupIED.stNum = config.setupIED.stNum;
            ConfigLoader.setupIED.sqNum = config.setupIED.sqNum;
        }
    }
}
