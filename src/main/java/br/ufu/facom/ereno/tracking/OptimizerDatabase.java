package br.ufu.facom.ereno.tracking;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Database for tracking optimizer results and best parameters found.
 * Allows resuming optimization from the best known configuration.
 * 
 * Database: optimizer_results.csv
 * Columns: optimizer_id, timestamp, attack_key, attack_combination, 
 *          optimizer_type, num_trials, best_metric_f1, best_parameters_json, 
 *          config_base_path, notes
 */
public class OptimizerDatabase {
    
    private static final Logger LOGGER = Logger.getLogger(OptimizerDatabase.class.getName());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final String databaseDirectory;
    private final String optimizerDbPath;
    
    /**
     * Initialize with default directory (target/tracking)
     */
    public OptimizerDatabase() {
        this("target/tracking");
    }
    
    /**
     * Initialize with custom directory
     */
    public OptimizerDatabase(String databaseDirectory) {
        this.databaseDirectory = databaseDirectory;
        this.optimizerDbPath = databaseDirectory + "/optimizer_results.csv";
        initializeDatabase();
    }
    
    /**
     * Initialize database file with headers if it doesn't exist
     */
    private void initializeDatabase() {
        try {
            File dir = new File(databaseDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
                LOGGER.info(() -> "Created optimizer database directory: " + databaseDirectory);
            }
            
            if (!new File(optimizerDbPath).exists()) {
                writeHeaders();
                LOGGER.info("Initialized optimizer results database");
            }
        } catch (IOException e) {
            LOGGER.severe(() -> "Failed to initialize optimizer database: " + e.getMessage());
            throw new RuntimeException("Optimizer database initialization failed", e);
        }
    }
    
    /**
     * Write CSV headers
     */
    private void writeHeaders() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(optimizerDbPath))) {
            writer.println("optimizer_id,timestamp,attack_key,attack_combination,optimizer_type," +
                    "num_trials,best_metric_f1,best_parameters_json,config_base_path,notes");
        }
    }
    
    /**
     * Generate unique optimizer ID
     */
    private static String generateOptimizerId() {
        return "OPT_" + System.currentTimeMillis() + "_" + 
               String.format("%04d", new Random().nextInt(10000));
    }
    
    /**
     * Escape CSV field
     */
    private static String escapeCsv(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
    
    /**
     * Result entry for optimizer database
     */
    public static class OptimizerResult {
        public String optimizerId;
        public String timestamp;
        public String attackKey;
        public String attackCombination; // comma-separated for combinations, empty for single attacks
        public String optimizerType; // "optuna_tpe", "optuna_cmaes", "java_hillclimb", etc.
        public int numTrials;
        public double bestMetricF1;
        public String bestParametersJson; // JSON string of parameters
        public String configBasePath;
        public String notes;
        
        public OptimizerResult() {
            this.optimizerId = generateOptimizerId();
            this.timestamp = DATE_FORMAT.format(new Date());
        }
        
        /**
         * Create from CSV line
         */
        public static OptimizerResult fromCsvLine(String line) {
            OptimizerResult result = new OptimizerResult();
            
            // Simple CSV parser (handles quoted fields)
            List<String> fields = parseCsvLine(line);
            if (fields.size() >= 10) {
                result.optimizerId = fields.get(0);
                result.timestamp = fields.get(1);
                result.attackKey = fields.get(2);
                result.attackCombination = fields.get(3);
                result.optimizerType = fields.get(4);
                try {
                    result.numTrials = Integer.parseInt(fields.get(5));
                    result.bestMetricF1 = Double.parseDouble(fields.get(6));
                } catch (NumberFormatException e) {
                    result.numTrials = 0;
                    result.bestMetricF1 = 1.0;
                }
                result.bestParametersJson = fields.get(7);
                result.configBasePath = fields.get(8);
                result.notes = fields.get(9);
            }
            
            return result;
        }
        
        /**
         * Simple CSV line parser that handles quoted fields
         */
        private static List<String> parseCsvLine(String line) {
            List<String> fields = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // skip next quote
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (c == ',' && !inQuotes) {
                    fields.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            fields.add(current.toString());
            
            return fields;
        }
        
        /**
         * Convert to CSV line
         */
        public String toCsvLine() {
            return String.join(",",
                escapeCsv(optimizerId),
                escapeCsv(timestamp),
                escapeCsv(attackKey),
                escapeCsv(attackCombination),
                escapeCsv(optimizerType),
                String.valueOf(numTrials),
                String.format("%.6f", bestMetricF1),
                escapeCsv(bestParametersJson),
                escapeCsv(configBasePath),
                escapeCsv(notes)
            );
        }
        
        /**
         * Get parameters as JsonObject
         */
        public JsonObject getParametersAsJson() {
            if (bestParametersJson == null || bestParametersJson.isEmpty()) {
                return new JsonObject();
            }
            try {
                return GSON.fromJson(bestParametersJson, JsonObject.class);
            } catch (com.google.gson.JsonSyntaxException | IllegalStateException e) {
                LOGGER.warning(() -> "Failed to parse parameters JSON: " + e.getMessage());
                return new JsonObject();
            }
        }
    }
    
    /**
     * Save an optimizer result to the database
     */
    public String saveResult(OptimizerResult result) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(optimizerDbPath, true))) {
            writer.println(result.toCsvLine());
            LOGGER.info(() -> "Saved optimizer result: " + result.optimizerId + 
                    " for attack: " + result.attackKey + " (F1: " + result.bestMetricF1 + ")");
            return result.optimizerId;
        }
    }
    
    /**
     * Get the best result for a specific attack
     */
    public OptimizerResult getBestResultForAttack(String attackKey) throws IOException {
        OptimizerResult best = null;
        
        if (!new File(optimizerDbPath).exists()) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(optimizerDbPath))) {
            reader.readLine(); // skip header
            String line;
            
            while ((line = reader.readLine()) != null) {
                OptimizerResult result = OptimizerResult.fromCsvLine(line);
                
                // Match attack key (exact match)
                if (result.attackKey.equals(attackKey)) {
                    // Keep the one with lowest F1 (best for evasion)
                    if (best == null || result.bestMetricF1 < best.bestMetricF1) {
                        best = result;
                    }
                }
            }
        }
        
        final OptimizerResult finalBest = best;
        if (finalBest != null) {
            LOGGER.info(() -> "Found best result for attack '" + attackKey + 
                    "': F1=" + finalBest.bestMetricF1 + " from " + finalBest.timestamp);
        }
        
        return best;
    }
    
    /**
     * Get the best result for an attack combination
     */
    public OptimizerResult getBestResultForCombination(List<String> attackKeys) throws IOException {
        String combinationKey = String.join(",", attackKeys);
        OptimizerResult best = null;
        
        if (!new File(optimizerDbPath).exists()) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(optimizerDbPath))) {
            reader.readLine(); // skip header
            String line;
            
            while ((line = reader.readLine()) != null) {
                OptimizerResult result = OptimizerResult.fromCsvLine(line);
                
                // Match combination (order-independent)
                if (matchesCombination(result.attackCombination, combinationKey)) {
                    if (best == null || result.bestMetricF1 < best.bestMetricF1) {
                        best = result;
                    }
                }
            }
        }
        
        final OptimizerResult finalBest = best;
        if (finalBest != null) {
            LOGGER.info(() -> "Found best result for combination '" + combinationKey + 
                    "': F1=" + finalBest.bestMetricF1 + " from " + finalBest.timestamp);
        }
        
        return best;
    }
    
    /**
     * Check if two attack combinations match (order-independent)
     */
    private boolean matchesCombination(String combo1, String combo2) {
        if (combo1 == null || combo2 == null) {
            return false;
        }
        
        String[] attacks1 = combo1.split(",");
        String[] attacks2 = combo2.split(",");
        
        if (attacks1.length != attacks2.length) {
            return false;
        }
        
        // Simple set comparison
        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(attacks1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(attacks2));
        
        return set1.equals(set2);
    }
    
    /**
     * Get all results for an attack (ordered by F1, best first)
     */
    public List<OptimizerResult> getAllResultsForAttack(String attackKey) throws IOException {
        List<OptimizerResult> results = new ArrayList<>();
        
        if (!new File(optimizerDbPath).exists()) {
            return results;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(optimizerDbPath))) {
            reader.readLine(); // skip header
            String line;
            
            while ((line = reader.readLine()) != null) {
                OptimizerResult result = OptimizerResult.fromCsvLine(line);
                if (result.attackKey.equals(attackKey)) {
                    results.add(result);
                }
            }
        }
        
        // Sort by F1 score (ascending - lower is better)
        results.sort((a, b) -> Double.compare(a.bestMetricF1, b.bestMetricF1));
        
        return results;
    }
    
    /**
     * Get all results from the database
     */
    public List<OptimizerResult> getAllResults() throws IOException {
        List<OptimizerResult> results = new ArrayList<>();
        
        if (!new File(optimizerDbPath).exists()) {
            return results;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(optimizerDbPath))) {
            reader.readLine(); // skip header
            String line;
            
            while ((line = reader.readLine()) != null) {
                results.add(OptimizerResult.fromCsvLine(line));
            }
        }
        
        return results;
    }
}
