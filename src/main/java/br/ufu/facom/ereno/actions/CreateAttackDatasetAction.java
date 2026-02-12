package br.ufu.facom.ereno.actions;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import br.ufu.facom.ereno.SubstationNetwork;
import br.ufu.facom.ereno.attacks.uc01.devices.RandomReplayerIED;
import br.ufu.facom.ereno.attacks.uc01.devices.RandomReplayerIEDC;
import br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED;
import br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIEDC;
import br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIED;
import br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIEDC;
import br.ufu.facom.ereno.attacks.uc04.devices.MasqueradeFakeNormalED;
import br.ufu.facom.ereno.attacks.uc05.devices.InjectorIED;
import br.ufu.facom.ereno.attacks.uc05.devices.InjectorIEDC;
import br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIED;
import br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIEDC;
import br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIED;
import br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIEDC;
import br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIED;
import br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIEDC;
import br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIED;
import br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIEDC;
import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.CSVWritter;
import br.ufu.facom.ereno.dataExtractors.DatasetWriter;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.startWriting;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.write;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.writeGooseMessagesToFile;
import static br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter.finishWriting;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.tracking.ExperimentTracker;
import br.ufu.facom.ereno.util.BenignDataManager;
import br.ufu.facom.ereno.util.Labels;

/**
 * Action handler for creating attack datasets.
 * 
 * This action:
 * 1. Loads benign data from file
 * 2. Generates attack segments based on configuration
 * 3. Combines segments into a unified attack dataset
 * 4. Outputs in ARFF or CSV format
 */
public class CreateAttackDatasetAction {
    
    private static final Logger LOGGER = Logger.getLogger(CreateAttackDatasetAction.class.getName());
    
    public static class Config {
        public String action;
        public InputConfig input;
        public OutputConfig output;
        public DatasetStructureConfig datasetStructure;
        public List<AttackSegmentConfig> attackSegments;
        
        public static class InputConfig {
            public String benignDataPath;
            public boolean verifyBenignData = true;
            public boolean useLegacy = false; // If true, use old school (non-C) attack implementations
        }
        
        public static class OutputConfig {
            public String directory;
            public String filename;
            public String format = "arff";
            public boolean enableTracking = true;
            public String experimentId; // Optional: link to existing experiment
        }
        
        public static class DatasetStructureConfig {
            public int messagesPerSegment = 1000;
            public boolean includeBenignSegment = true;
            public boolean shuffleSegments = false;
            public boolean binaryClassification = false; // If true, map all attacks to "attack" label
        }
        
        public static class AttackSegmentConfig {
            public String name;
            public boolean enabled = true;
            public String attackConfig;
            public String description;
            // For attack combinations
            public List<String> attacks;
            public List<String> attackConfigs;
        }
    }
    
    public static void execute(String configPath) throws Exception {
        LOGGER.info(() -> "Starting CreateAttackDatasetAction with config: " + configPath);
        
        // Load configuration
        Config config = loadConfig(configPath);
        
        // Verify benign data exists
        if (config.input.verifyBenignData) {
            File benignFile = new File(config.input.benignDataPath);
            if (!benignFile.exists()) {
                throw new IOException("Benign data file not found: " + config.input.benignDataPath);
            }
            LOGGER.info(() -> "Verified benign data file exists: " + config.input.benignDataPath);
        }
        
        // Ensure output directory exists
        File outputDir = new File(config.output.directory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
            LOGGER.info(() -> "Created output directory: " + config.output.directory);
        }
        
        // Log attack implementation mode
        String attackMode = config.input.useLegacy ? "LEGACY (non-C)" : "CONFIGURABLE (C)";
        LOGGER.info(() -> "Attack implementation mode: " + attackMode);
        
        // Load benign data
        LOGGER.info(() -> "Loading benign data from: " + config.input.benignDataPath);
        LegitimateProtectionIED benignIED = BenignDataManager.loadBenignData(config.input.benignDataPath);
        LOGGER.info(() -> "Loaded " + benignIED.getNumberOfMessages() + " benign messages");
        
        // Set up SubstationNetwork with benign messages for attack simulations
        SubstationNetwork network = new SubstationNetwork();
        network.stationBusMessages.addAll(benignIED.getMessages());
        benignIED.setSubstationNetwork(network);
        LOGGER.info(() -> "Initialized SubstationNetwork with " + network.stationBusMessages.size() + " messages");
        
        // Prepare output file
        String outputPath = new File(config.output.directory, config.output.filename).getAbsolutePath();
        boolean csvMode = "csv".equalsIgnoreCase(config.output.format);
        
        // Start writing
        if (csvMode) {
            CSVWritter.startWriting(outputPath);
        } else {
            // Set binary classification mode if configured
            DatasetWriter.binaryClassificationMode = config.datasetStructure.binaryClassification;
            startWriting(outputPath);
            write("@relation ereno_attack_dataset");
        }
        
        // Collect all segments
        List<SegmentData> segments = new ArrayList<>();
        
        // Add benign segment if configured
        if (config.datasetStructure.includeBenignSegment) {
            SegmentData benignSegment = new SegmentData();
            benignSegment.name = "benign";
            benignSegment.messages = extractMessages(benignIED, config.datasetStructure.messagesPerSegment);
            segments.add(benignSegment);
            LOGGER.info(() -> "Added benign segment with " + benignSegment.messages.size() + " messages");
        }
        
        // Generate attack segments
        for (Config.AttackSegmentConfig attackSegment : config.attackSegments) {
            if (!attackSegment.enabled) {
                LOGGER.info(() -> "Skipping disabled attack segment: " + attackSegment.name);
                continue;
            }
            
            // Check if this is a combination attack
            if (attackSegment.attacks != null && !attackSegment.attacks.isEmpty()) {
                // Handle attack combination
                LOGGER.info(() -> "Generating combination attack segment: " + attackSegment.name);
                SegmentData combinedSegment = generateCombinationSegment(
                    attackSegment, benignIED, config.datasetStructure.messagesPerSegment, config.input.useLegacy);
                segments.add(combinedSegment);
            } else {
                // Single attack
                LOGGER.info(() -> "Generating attack segment: " + attackSegment.name);
                SegmentData segment = generateAttackSegment(
                    attackSegment, benignIED, config.datasetStructure.messagesPerSegment, config.input.useLegacy);
                segments.add(segment);
            }
        }
        
        // Shuffle segments if configured
        if (config.datasetStructure.shuffleSegments) {
            Collections.shuffle(segments, ConfigLoader.RNG);
            LOGGER.info("Shuffled segment order");
        }
        
        // Write all segments to file
        int totalMessages = 0;
        boolean isFirstSegment = true;
        for (SegmentData segment : segments) {
            LOGGER.info(() -> "Writing segment '" + segment.name + "' with " + segment.messages.size() + " messages");
            
            // DEBUG: Check what labels messages have before processing
            if (config.datasetStructure.binaryClassification) {
                Map<String, Integer> labelCounts = new HashMap<>();
                for (Goose message : segment.messages) {
                    String label = message.getLabel();
                    labelCounts.put(label, labelCounts.getOrDefault(label, 0) + 1);
                }
                LOGGER.info(() -> "Segment '" + segment.name + "' original label distribution: " + labelCounts);
            }
            
            // Label messages based on configuration
            if (config.datasetStructure.binaryClassification) {
                // Binary classification: Check each message's label from attack creator
                // Messages labeled by attacks (uc01-uc08) are "attack", normal messages stay "normal"
                for (Goose message : segment.messages) {
                    String originalLabel = message.getLabel();
                    
                    // Map attack-specific labels to binary "attack" label
                    // Attack creators set labels like "random_replay", "injection", etc.
                    if (originalLabel != null && !originalLabel.equals("normal")) {
                        // This message was labeled by an attack creator - mark as attack
                        message.setLabel("attack");
                    } else {
                        // This is a legitimate message - keep as normal
                        message.setLabel("normal");
                    }
                }
            } else {
                // Multi-class mode: use segment-level labeling
                String segmentLabel = getLabelForSegmentName(segment.name);
                for (Goose message : segment.messages) {
                    message.setLabel(segmentLabel);
                }
            }
            
            if (csvMode) {
                // CSV mode not fully supported for segments yet, use ARFF
                writeGooseMessagesToFile(segment.messages, isFirstSegment);
            } else {
                writeGooseMessagesToFile(segment.messages, isFirstSegment);
            }
            
            totalMessages += segment.messages.size();
            isFirstSegment = false;
        }
        
        // Finish writing
        if (csvMode) {
            CSVWritter.finishWriting();
        } else {
            finishWriting();
        }
        
        LOGGER.info(() -> "Attack dataset created successfully: " + outputPath);
        final int finalTotalMessages = totalMessages;
        final int finalSegmentSize = segments.size();
        LOGGER.info(() -> "Total messages: " + finalTotalMessages + " across " + finalSegmentSize + " segments");
        
        // Track dataset creation
        if (config.output.enableTracking) {
            trackDatasetCreation(config, outputPath, totalMessages, segments, configPath);
        }
    }
    
    private static void trackDatasetCreation(Config config, String outputPath, 
                                            int totalMessages, List<SegmentData> segments,
                                            String configPath) {
        try {
            ExperimentTracker tracker = new ExperimentTracker();
            
            // Determine experiment ID
            String experimentId = config.output.experimentId;
            if (experimentId == null || experimentId.isEmpty()) {
                // Create a new standalone experiment
                experimentId = tracker.startExperiment(
                    "attack_dataset_creation",
                    "Create attack dataset: " + config.output.filename,
                    configPath,
                    "Standalone attack dataset creation"
                );
            }
            
            // Collect attack types
            List<String> attackTypes = new ArrayList<>();
            for (Config.AttackSegmentConfig segment : config.attackSegments) {
                if (segment.enabled) {
                    if (segment.attacks != null && !segment.attacks.isEmpty()) {
                        attackTypes.addAll(segment.attacks);
                    } else {
                        attackTypes.add(segment.name);
                    }
                }
            }
            
            // Build dataset structure info
            Map<String, Object> structureInfo = new HashMap<>();
            structureInfo.put("messagesPerSegment", config.datasetStructure.messagesPerSegment);
            structureInfo.put("includeBenignSegment", config.datasetStructure.includeBenignSegment);
            structureInfo.put("shuffleSegments", config.datasetStructure.shuffleSegments);
            structureInfo.put("totalSegments", segments.size());
            structureInfo.put("segmentNames", segments.stream()
                .map(s -> s.name)
                .collect(java.util.stream.Collectors.toList()));
            
            // Get random seed from ConfigLoader if available
            String randomSeed = "unknown";
            try {
                if (ConfigLoader.RNG != null) {
                    randomSeed = "from_RNG";
                }
            } catch (Exception e) {
                // Random seed not available
            }
            
            // Track the dataset
            String datasetId = tracker.trackAttackDataset(
                experimentId,
                outputPath,
                configPath,
                String.join(", ", attackTypes),
                randomSeed,
                structureInfo,
                "Attack dataset with " + totalMessages + " messages"
            );
            
            LOGGER.info(() -> "Dataset tracked with ID: " + datasetId);
            
            // Complete experiment if we created it
            if (config.output.experimentId == null || config.output.experimentId.isEmpty()) {
                tracker.completeExperiment(experimentId);
            }
            
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to track dataset creation: " + e.getMessage());
            // Don't fail the action if tracking fails
        }
    }
    
    private static String getLabelForSegmentName(String segmentName) {
        // Map segment names to label values
        if (segmentName.equals("benign")) {
            return Labels.LABELS[0]; // "normal"
        } else if (segmentName.startsWith("uc01")) {
            return Labels.LABELS[1]; // "random_replay"
        } else if (segmentName.startsWith("uc02")) {
            return Labels.LABELS[2]; // "inverse_replay"
        } else if (segmentName.startsWith("uc03")) {
            return Labels.LABELS[3]; // "masquerade_fake_fault"
        } else if (segmentName.startsWith("uc04")) {
            return Labels.LABELS[4]; // "masquerade_fake_normal"
        } else if (segmentName.startsWith("uc05")) {
            return Labels.LABELS[5]; // "injection"
        } else if (segmentName.startsWith("uc06")) {
            return Labels.LABELS[6]; // "high_StNum"
        } else if (segmentName.startsWith("uc07")) {
            return Labels.LABELS[7]; // "poisoned_high_rate"
        } else if (segmentName.startsWith("uc08")) {
            return Labels.LABELS[8]; // "grayhole"
        } else if (segmentName.startsWith("uc10")) {
            return Labels.LABELS[9]; // "delayed_replay"
        }
        return Labels.LABELS[0]; // default to "normal"
    }

    private static String getLabelForSegmentNameBinary(String segmentName) {
        // For binary classification: normal vs any attack
        if (segmentName.equals("benign")) {
            return Labels.BINARY_LABELS[0]; // "normal"
        } else {
            return Labels.BINARY_LABELS[1]; // "attack"
        }
    }

    private static Config loadConfig(String path) throws IOException {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Config.class);
        }
    }
    
    public static SegmentData generateAttackSegment(
            Config.AttackSegmentConfig segmentConfig,
            LegitimateProtectionIED benignIED,
            int messagesPerSegment,
            boolean useLegacy) throws Exception {
        
        // Load attack-specific config
        AttackConfig attackConfig = loadAttackConfig(segmentConfig.attackConfig);
        
        // Extract attack type from config
        String attackType = attackConfig.getAttackType();
        
        // Create attack device based on type
        ProtectionIED attackIED = createAttackDevice(attackType, attackConfig, benignIED, useLegacy);
        
        // Set up network connection for attack device
        attackIED.setSubstationNetwork(benignIED.getSubstationNetwork());
        
        // Generate attack messages
        attackIED.run(messagesPerSegment);
        
        // Create segment
        SegmentData segment = new SegmentData();
        segment.name = segmentConfig.name;
        segment.messages = attackIED.copyMessages();
        
        LOGGER.info(() -> "Generated " + segment.messages.size() + " messages for attack: " + segmentConfig.name);
        
        return segment;
    }
    
    public static SegmentData generateCombinationSegment(
            Config.AttackSegmentConfig segmentConfig,
            LegitimateProtectionIED benignIED,
            int messagesPerSegment,
            boolean useLegacy) throws Exception {
        
        SegmentData combinedSegment = new SegmentData();
        combinedSegment.name = segmentConfig.name;
        combinedSegment.messages = new ArrayList<>();
        
        int messagesPerAttack = messagesPerSegment / segmentConfig.attacks.size();
        int remainder = messagesPerSegment % segmentConfig.attacks.size();
        
        // Generate messages for each attack in the combination
        for (int i = 0; i < segmentConfig.attacks.size(); i++) {
            String attackName = segmentConfig.attacks.get(i);
            String attackConfigPath = segmentConfig.attackConfigs.get(i);
            
            AttackConfig attackConfig = loadAttackConfig(attackConfigPath);
            String attackType = attackConfig.getAttackType();
            ProtectionIED attackIED = createAttackDevice(attackType, attackConfig, benignIED, useLegacy);
            
            // Add remainder to last attack
            int count = (i == segmentConfig.attacks.size() - 1) ? 
                messagesPerAttack + remainder : messagesPerAttack;
            
            attackIED.run(count);
            combinedSegment.messages.addAll(attackIED.copyMessages());
            
            LOGGER.info(() -> "Added " + count + " messages for attack '" + attackName + 
                "' in combination '" + segmentConfig.name + "'");
        }
        
        // Shuffle messages within the combination
        Collections.shuffle(combinedSegment.messages, ConfigLoader.RNG);
        
        return combinedSegment;
    }
    
    private static AttackConfig loadAttackConfig(String path) throws IOException {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            return new AttackConfig(json);
        }
    }
    
    private static ProtectionIED createAttackDevice(
            String attackType, 
            AttackConfig attackConfig,
            LegitimateProtectionIED benignIED,
            boolean useLegacy) throws Exception {
        
        switch (attackType.toLowerCase()) {
            case "random_replay":
                return useLegacy ? new RandomReplayerIED(benignIED) : new RandomReplayerIEDC(benignIED, attackConfig);
                
            case "inverse_replay":
                return useLegacy ? new InverseReplayerIED(benignIED) : new InverseReplayerIEDC(benignIED, attackConfig);
                
            case "masquerade_fault":
                return useLegacy ? new MasqueradeFakeFaultIED(benignIED) : new MasqueradeFakeFaultIEDC(benignIED, attackConfig);
                
            case "masquerade_normal":
                return new MasqueradeFakeNormalED();
                
            case "random_injection":
            case "injection":
                return useLegacy ? new InjectorIED(benignIED) : new InjectorIEDC(benignIED, attackConfig);
                
            case "high_stnum_injection":
                return useLegacy ? new HighStNumInjectorIED(benignIED) : new HighStNumInjectorIEDC(benignIED, attackConfig);
                
            case "flooding":
            case "high_rate_stnum_injection":
                return useLegacy ? new HighRateStNumInjectorIED(benignIED) : new HighRateStNumInjectorIEDC(benignIED, attackConfig);
                
            case "grayhole":
            case "greyhole":
                if (useLegacy) {
                    // Legacy version doesn't use config parameters
                    return new GrayHoleVictimIED(benignIED);
                } else {
                    // C variant uses config parameters from attack config file
                    return new GrayHoleVictimIEDC(benignIED, attackConfig);
                }
            case "delayed_replay":
                return useLegacy ? new DelayedReplayIED(benignIED) : new DelayedReplayIEDC(benignIED, attackConfig);
                
            default:
                throw new IllegalArgumentException("Unknown attack type: " + attackType);
        }
    }
    
    private static ArrayList<Goose> extractMessages(LegitimateProtectionIED ied, int count) {
        ArrayList<Goose> messages = ied.copyMessages();
        if (messages.size() > count) {
            return new ArrayList<>(messages.subList(0, count));
        }
        return messages;
    }
    
    public static class SegmentData {
        public String name;
        public ArrayList<Goose> messages;
    }
}
