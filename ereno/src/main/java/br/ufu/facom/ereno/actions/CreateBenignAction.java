package br.ufu.facom.ereno.actions;

import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.config.ActionConfigLoader;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.util.BenignDataManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

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
        }

            LOGGER.info("=== Create Benign Data Action Completed Successfully ===");
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
