package br.ufu.facom.ereno.actions;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.startWriting;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.write;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.writeGooseMessagesToFile;
import static br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter.finishWriting;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;
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
        }
        
        public static class OutputConfig {
            public String directory;
            public String filename;
            public String format = "arff";
        }
        
        public static class DatasetStructureConfig {
            public int messagesPerSegment = 1000;
            public boolean includeBenignSegment = true;
            public boolean shuffleSegments = false;
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
        LOGGER.info("Starting CreateAttackDatasetAction with config: " + configPath);
        
        // Load configuration
        Config config = loadConfig(configPath);
        
        // Verify benign data exists
        if (config.input.verifyBenignData) {
            File benignFile = new File(config.input.benignDataPath);
            if (!benignFile.exists()) {
                throw new IOException("Benign data file not found: " + config.input.benignDataPath);
            }
            LOGGER.info("Verified benign data file exists: " + config.input.benignDataPath);
        }
        
        // Ensure output directory exists
        File outputDir = new File(config.output.directory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
            LOGGER.info("Created output directory: " + config.output.directory);
        }
        
        // Load benign data
        LOGGER.info("Loading benign data from: " + config.input.benignDataPath);
        LegitimateProtectionIED benignIED = BenignDataManager.loadBenignData(config.input.benignDataPath);
        LOGGER.info("Loaded " + benignIED.getNumberOfMessages() + " benign messages");
        
        // Set up SubstationNetwork with benign messages for attack simulations
        SubstationNetwork network = new SubstationNetwork();
        network.stationBusMessages.addAll(benignIED.getMessages());
        benignIED.setSubstationNetwork(network);
        LOGGER.info("Initialized SubstationNetwork with " + network.stationBusMessages.size() + " messages");
        
        // Prepare output file
        String outputPath = new File(config.output.directory, config.output.filename).getAbsolutePath();
        boolean csvMode = "csv".equalsIgnoreCase(config.output.format);
        
        // Start writing
        if (csvMode) {
            CSVWritter.startWriting(outputPath);
        } else {
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
            LOGGER.info("Added benign segment with " + benignSegment.messages.size() + " messages");
        }
        
        // Generate attack segments
        for (Config.AttackSegmentConfig attackSegment : config.attackSegments) {
            if (!attackSegment.enabled) {
                LOGGER.info("Skipping disabled attack segment: " + attackSegment.name);
                continue;
            }
            
            // Check if this is a combination attack
            if (attackSegment.attacks != null && !attackSegment.attacks.isEmpty()) {
                // Handle attack combination
                LOGGER.info("Generating combination attack segment: " + attackSegment.name);
                SegmentData combinedSegment = generateCombinationSegment(
                    attackSegment, benignIED, config.datasetStructure.messagesPerSegment, false);
                segments.add(combinedSegment);
            } else {
                // Single attack
                LOGGER.info("Generating attack segment: " + attackSegment.name);
                SegmentData segment = generateAttackSegment(
                    attackSegment, benignIED, config.datasetStructure.messagesPerSegment, false);
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
            LOGGER.info("Writing segment '" + segment.name + "' with " + segment.messages.size() + " messages");
            
            // Ensure all messages in this segment have the correct label
            String segmentLabel = getLabelForSegmentName(segment.name);
            for (Goose message : segment.messages) {
                message.setLabel(segmentLabel);
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
        
        LOGGER.info("Attack dataset created successfully: " + outputPath);
        LOGGER.info("Total messages: " + totalMessages + " across " + segments.size() + " segments");
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
        
        LOGGER.info("Generated " + segment.messages.size() + " messages for attack: " + segmentConfig.name);
        
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
            
            LOGGER.info("Added " + count + " messages for attack '" + attackName + 
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
