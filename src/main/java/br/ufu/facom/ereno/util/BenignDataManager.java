package br.ufu.facom.ereno.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.CSVWritter;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.messages.Goose;

/**
 * Manages saving and loading of benign datasets.
 * Benign data files are named: {randomSeed}_{faultProbability}%fault_benign_data.{ext}
 */
public class BenignDataManager {

    private static final Logger LOGGER = Logger.getLogger(BenignDataManager.class.getName());

    /**
     * Generates the filename for benign data based on current config.
     * Format: {randomSeed}_{faultProbability}%fault_benign_data.{ext}
     */
    public static String generateBenignDataFilename(String format) {
        long seed = ConfigLoader.randomSeed != null ? ConfigLoader.randomSeed : System.nanoTime();
        int faultProb = ConfigLoader.benignData.faultProbability;
        String ext = "csv".equalsIgnoreCase(format) ? "csv" : "arff";
        return seed + "_" + faultProb + "%fault_benign_data." + ext;
    }

    /**
     * Gets the full path for saving benign data.
     */
    public static String getBenignDataPath(String format) {
        String dir = ConfigLoader.benignData.benignDataDir;
        String filename = generateBenignDataFilename(format);
        return dir + File.separator + filename;
    }

    /**
     * Saves benign GOOSE messages to a file in the configured directory.
     * Creates the directory if it doesn't exist.
     */
    public static void saveBenignData(ArrayList<Goose> messages, String format) throws IOException {
        String dirPath = ConfigLoader.benignData.benignDataDir;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
            LOGGER.info("Created benign data directory: " + dirPath);
        }

        String filepath = getBenignDataPath(format);
        boolean csvMode = "csv".equalsIgnoreCase(format);

        LOGGER.info("Saving " + messages.size() + " benign messages to: " + filepath);

        if (csvMode) {
            saveBenignDataCsv(messages, filepath);
        } else {
            saveBenignDataArff(messages, filepath);
        }

        LOGGER.info("Benign data saved successfully.");
    }

    /**
     * Saves benign data in CSV format.
     */
    private static void saveBenignDataCsv(ArrayList<Goose> messages, String filepath) throws IOException {
        CSVWritter.startWriting(filepath);
        CSVWritter.writeDefaultHeader();
        
        Goose prev = null;
        for (Goose gm : messages) {
            if (prev != null) {
                String gooseString = gm.asCSVFull();
                String gooseConsistency = br.ufu.facom.ereno.featureEngineering.IntermessageCorrelation.getConsistencyFeaturesAsCSV(gm, prev);
                String line = gooseString + "," + gooseConsistency + "," + gm.getLabel();
                CSVWritter.writeLine(line);
            }
            prev = gm.copy();
        }
        
        CSVWritter.finishWriting();
    }

    /**
     * Saves benign data in ARFF format.
     */
    private static void saveBenignDataArff(ArrayList<Goose> messages, String filepath) throws IOException {
        GSVDatasetWriter.startWriting(filepath);
        GSVDatasetWriter.write("@relation benign_data");
        GSVDatasetWriter.writeGooseMessagesToFile(messages, true);
        GSVDatasetWriter.finishWriting();
    }

    /**
     * Loads benign GOOSE messages from a file.
     * Supports both CSV and ARFF formats (detects by extension).
     */
    public static LegitimateProtectionIED loadBenignData(String filepath) throws IOException {
        File file = new File(filepath);
        if (!file.exists()) {
            throw new FileNotFoundException("Benign data file not found: " + filepath);
        }

        LOGGER.info("Loading benign data from: " + filepath);

        LegitimateProtectionIED ied = new LegitimateProtectionIED();
        ArrayList<Goose> messages;

        if (filepath.toLowerCase().endsWith(".csv")) {
            messages = loadBenignDataCsv(filepath);
        } else if (filepath.toLowerCase().endsWith(".arff")) {
            messages = loadBenignDataArff(filepath);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Use .csv or .arff");
        }

        ied.setMessages(messages);
        LOGGER.info("Loaded " + messages.size() + " benign messages.");
        return ied;
    }

    /**
     * Loads benign data from CSV format.
     */
    private static ArrayList<Goose> loadBenignDataCsv(String filepath) throws IOException {
        ArrayList<Goose> messages = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            boolean headerSkipped = false;
            
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                // Skip header line
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                // Parse CSV line into Goose message
                Goose goose = parseGooseFromCsv(line);
                if (goose != null) {
                    messages.add(goose);
                }
            }
        }
        
        return messages;
    }

    /**
     * Loads benign data from ARFF format.
     */
    private static ArrayList<Goose> loadBenignDataArff(String filepath) throws IOException {
        ArrayList<Goose> messages = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            boolean inData = false;
            
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("%")) continue;
                
                if (line.toLowerCase().startsWith("@data")) {
                    inData = true;
                    continue;
                }
                
                if (inData) {
                    // Parse ARFF data line into Goose message
                    Goose goose = parseGooseFromCsv(line);  // Same format as CSV
                    if (goose != null) {
                        messages.add(goose);
                    }
                }
            }
        }
        
        return messages;
    }

    /**
     * Parses a Goose message from a CSV line.
     * Expected format from writeGooseMessagesToFile():
     * t, GooseTimestamp, SqNum, StNum, cbStatus, frameLen, ethDst, ethSrc, ethType, 
     * gooseTimeAllowedtoLive, gooseAppid, gooseLen, TPID, gocbRef, datSet, goID, test, 
     * confRev, ndsCom, numDatSetEntries, APDUSize, protocol, stDiff, sqDiff, 
     * gooseLengthDiff, cbStatusDiff, apduSizeDiff, frameLengthDiff, timestampDiff, label
     */
    private static Goose parseGooseFromCsv(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length < 29) return null;
            
            // Extract fields based on writeGooseMessagesToFile() output order
            double t = Double.parseDouble(parts[0].trim());
            double timestamp = Double.parseDouble(parts[1].trim());
            int sqNum = Integer.parseInt(parts[2].trim());
            int stNum = Integer.parseInt(parts[3].trim());
            int cbStatus = Integer.parseInt(parts[4].trim());
            // Label is always the last field in ARFF format
            String label = parts.length > 29 ? parts[parts.length - 1].trim() : "normal";
            
            return new Goose(cbStatus, stNum, sqNum, timestamp, t, label);
        } catch (Exception e) {
            LOGGER.warning("Failed to parse line: " + line + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Lists all available benign data files in the configured directory.
     */
    public static ArrayList<String> listBenignDataFiles() {
        ArrayList<String> files = new ArrayList<>();
        String dirPath = ConfigLoader.benignData.benignDataDir;
        File dir = new File(dirPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return files;
        }
        
        File[] fileList = dir.listFiles((d, name) -> 
            name.endsWith("benign_data.csv") || name.endsWith("benign_data.arff"));
        
        if (fileList != null) {
            for (File f : fileList) {
                files.add(f.getName());
            }
        }
        
        return files;
    }
}
