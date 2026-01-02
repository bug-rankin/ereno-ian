package br.ufu.facom.ereno.tracking;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Central manager for CSV-based tracking databases.
 * Handles operations for Experiment, Dataset/Configuration, Model, and Results databases.
 * 
 * Database Structure:
 * - experiments.csv: Overall experiment tracking
 * - datasets.csv: Dataset and configuration tracking
 * - models.csv: Trained model tracking
 * - results.csv: Evaluation results tracking
 * 
 * Cross-referencing: Each database uses unique IDs that can reference other databases
 */
public class DatabaseManager {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    private final String databaseDirectory;
    private final String experimentsDbPath;
    private final String datasetsDbPath;
    private final String modelsDbPath;
    private final String resultsDbPath;
    
    /**
     * Initialize database manager with default directory (target/tracking)
     */
    public DatabaseManager() {
        this("target/tracking");
    }
    
    /**
     * Initialize database manager with custom directory
     */
    public DatabaseManager(String databaseDirectory) {
        this.databaseDirectory = databaseDirectory;
        this.experimentsDbPath = databaseDirectory + "/experiments.csv";
        this.datasetsDbPath = databaseDirectory + "/datasets.csv";
        this.modelsDbPath = databaseDirectory + "/models.csv";
        this.resultsDbPath = databaseDirectory + "/results.csv";
        
        initializeDatabases();
    }
    
    /**
     * Initialize all database files with headers if they don't exist
     */
    private void initializeDatabases() {
        try {
            // Create directory if it doesn't exist
            File dir = new File(databaseDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
                LOGGER.info(() -> "Created tracking database directory: " + databaseDirectory);
            }
            
            // Initialize experiments database
            if (!new File(experimentsDbPath).exists()) {
                writeHeaders(experimentsDbPath, ExperimentEntry.getHeaders());
                LOGGER.info("Initialized experiments database");
            }
            
            // Initialize datasets database
            if (!new File(datasetsDbPath).exists()) {
                writeHeaders(datasetsDbPath, DatasetEntry.getHeaders());
                LOGGER.info("Initialized datasets database");
            }
            
            // Initialize models database
            if (!new File(modelsDbPath).exists()) {
                writeHeaders(modelsDbPath, ModelEntry.getHeaders());
                LOGGER.info("Initialized models database");
            }
            
            // Initialize results database
            if (!new File(resultsDbPath).exists()) {
                writeHeaders(resultsDbPath, ResultEntry.getHeaders());
                LOGGER.info("Initialized results database");
            }
            
        } catch (IOException e) {
            LOGGER.severe(() -> "Failed to initialize databases: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Write CSV headers to a new file
     */
    private void writeHeaders(String filePath, String[] headers) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(String.join(",", headers));
        }
    }
    
    /**
     * Generate a unique ID based on timestamp and random component
     */
    public static String generateUniqueId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + 
               String.format("%04d", new Random().nextInt(10000));
    }
    
    /**
     * Escape CSV field (handle commas and quotes)
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
     * Append an entry to a CSV database
     */
    private void appendEntry(String dbPath, String[] values) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(dbPath, true))) {
            String[] escapedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escapedValues[i] = escapeCsv(values[i]);
            }
            writer.println(String.join(",", escapedValues));
        }
    }
    
    // ==================== EXPERIMENT TRACKING ====================
    
    public static class ExperimentEntry {
        public String experimentId;
        public String timestamp;
        public String experimentType; // e.g., "random_seed_analysis", "attack_specific", "parameter_optimization"
        public String description;
        public String pipelineConfigPath;
        public String status; // "running", "completed", "failed"
        public String notes;
        
        public static String[] getHeaders() {
            return new String[]{
                "experiment_id", "timestamp", "experiment_type", "description",
                "pipeline_config_path", "status", "notes"
            };
        }
        
        public String[] toValues() {
            return new String[]{
                experimentId,
                timestamp,
                experimentType,
                description,
                pipelineConfigPath,
                status,
                notes
            };
        }
    }
    
    /**
     * Log a new experiment
     */
    public String logExperiment(String experimentType, String description, String pipelineConfigPath, String notes) throws IOException {
        String experimentId = generateUniqueId("EXP");
        
        ExperimentEntry entry = new ExperimentEntry();
        entry.experimentId = experimentId;
        entry.timestamp = DATE_FORMAT.format(new Date());
        entry.experimentType = experimentType;
        entry.description = description;
        entry.pipelineConfigPath = pipelineConfigPath;
        entry.status = "running";
        entry.notes = notes;
        
        appendEntry(experimentsDbPath, entry.toValues());
        LOGGER.info(() -> "Logged experiment: " + experimentId);
        
        return experimentId;
    }
    
    /**
     * Update experiment status
     */
    public void updateExperimentStatus(String experimentId, String status) throws IOException {
        // Read all entries
        List<String> lines = Files.readAllLines(Paths.get(experimentsDbPath));
        
        // Update matching entry
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).startsWith(experimentId + ",")) {
                String[] parts = lines.get(i).split(",", -1);
                parts[5] = status; // status column
                lines.set(i, String.join(",", parts));
                break;
            }
        }
        
        // Write back
        Files.write(Paths.get(experimentsDbPath), lines);
        LOGGER.info(() -> "Updated experiment " + experimentId + " status to: " + status);
    }
    
    // ==================== DATASET TRACKING ====================
    
    public static class DatasetEntry {
        public String datasetId;
        public String timestamp;
        public String experimentId; // cross-reference
        public String datasetType; // "benign", "attack", "test", "training"
        public String filePath;
        public String format; // "arff", "csv"
        public int numInstances;
        public int numAttributes;
        public String configPath;
        public String attackTypes; // comma-separated if multiple
        public String randomSeed;
        public String datasetStructure; // JSON or key parameters
        public String sourceFiles; // reference to input files used
        public String notes;
        
        public static String[] getHeaders() {
            return new String[]{
                "dataset_id", "timestamp", "experiment_id", "dataset_type", "file_path",
                "format", "num_instances", "num_attributes", "config_path", "attack_types",
                "random_seed", "dataset_structure", "source_files", "notes"
            };
        }
        
        public String[] toValues() {
            return new String[]{
                datasetId,
                timestamp,
                experimentId,
                datasetType,
                filePath,
                format,
                String.valueOf(numInstances),
                String.valueOf(numAttributes),
                configPath,
                attackTypes,
                randomSeed,
                datasetStructure,
                sourceFiles,
                notes
            };
        }
    }
    
    /**
     * Log a new dataset
     */
    public String logDataset(DatasetEntry entry) throws IOException {
        if (entry.datasetId == null || entry.datasetId.isEmpty()) {
            entry.datasetId = generateUniqueId("DS");
        }
        if (entry.timestamp == null || entry.timestamp.isEmpty()) {
            entry.timestamp = DATE_FORMAT.format(new Date());
        }
        
        appendEntry(datasetsDbPath, entry.toValues());
        LOGGER.info(() -> "Logged dataset: " + entry.datasetId + " at " + entry.filePath);
        
        return entry.datasetId;
    }
    
    // ==================== MODEL TRACKING ====================
    
    public static class ModelEntry {
        public String modelId;
        public String timestamp;
        public String experimentId; // cross-reference
        public String datasetId; // cross-reference to training dataset
        public String classifierName; // "J48", "RandomForest", etc.
        public String modelPath;
        public long trainingTimeMs;
        public String hyperparameters; // JSON or key-value
        public String configPath;
        public String notes;
        
        public static String[] getHeaders() {
            return new String[]{
                "model_id", "timestamp", "experiment_id", "dataset_id", "classifier_name",
                "model_path", "training_time_ms", "hyperparameters", "config_path", "notes"
            };
        }
        
        public String[] toValues() {
            return new String[]{
                modelId,
                timestamp,
                experimentId,
                datasetId,
                classifierName,
                modelPath,
                String.valueOf(trainingTimeMs),
                hyperparameters,
                configPath,
                notes
            };
        }
    }
    
    /**
     * Log a new model
     */
    public String logModel(ModelEntry entry) throws IOException {
        if (entry.modelId == null || entry.modelId.isEmpty()) {
            entry.modelId = generateUniqueId("MDL");
        }
        if (entry.timestamp == null || entry.timestamp.isEmpty()) {
            entry.timestamp = DATE_FORMAT.format(new Date());
        }
        
        appendEntry(modelsDbPath, entry.toValues());
        LOGGER.info(() -> "Logged model: " + entry.modelId + " (" + entry.classifierName + ")");
        
        return entry.modelId;
    }
    
    // ==================== RESULTS TRACKING ====================
    
    public static class ResultEntry {
        public String resultId;
        public String timestamp;
        public String experimentId; // cross-reference
        public String modelId; // cross-reference
        public String testDatasetId; // cross-reference
        public double accuracy;
        public double precision;
        public double recall;
        public double f1Score;
        public int truePositives;
        public int trueNegatives;
        public int falsePositives;
        public int falseNegatives;
        public long evaluationTimeMs;
        public String confusionMatrix; // JSON representation
        public String configPath;
        public String notes;
        
        public static String[] getHeaders() {
            return new String[]{
                "result_id", "timestamp", "experiment_id", "model_id", "test_dataset_id",
                "accuracy", "precision", "recall", "f1_score", "true_positives",
                "true_negatives", "false_positives", "false_negatives", "evaluation_time_ms",
                "confusion_matrix", "config_path", "notes"
            };
        }
        
        public String[] toValues() {
            return new String[]{
                resultId,
                timestamp,
                experimentId,
                modelId,
                testDatasetId,
                String.valueOf(accuracy),
                String.valueOf(precision),
                String.valueOf(recall),
                String.valueOf(f1Score),
                String.valueOf(truePositives),
                String.valueOf(trueNegatives),
                String.valueOf(falsePositives),
                String.valueOf(falseNegatives),
                String.valueOf(evaluationTimeMs),
                confusionMatrix,
                configPath,
                notes
            };
        }
    }
    
    /**
     * Log a new result
     */
    public String logResult(ResultEntry entry) throws IOException {
        if (entry.resultId == null || entry.resultId.isEmpty()) {
            entry.resultId = generateUniqueId("RES");
        }
        if (entry.timestamp == null || entry.timestamp.isEmpty()) {
            entry.timestamp = DATE_FORMAT.format(new Date());
        }
        
        appendEntry(resultsDbPath, entry.toValues());
        LOGGER.info(() -> "Logged result: " + entry.resultId);
        
        return entry.resultId;
    }
    
    // ==================== QUERY METHODS ====================
    
    /**
     * Get all entries from a database that match a filter
     */
    public List<String[]> queryDatabase(String dbPath, String columnName, String value) throws IOException {
        List<String[]> results = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        
        if (lines.isEmpty()) {
            return results;
        }
        
        // Find column index
        String[] headers = lines.get(0).split(",", -1);
        int columnIndex = -1;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(columnName)) {
                columnIndex = i;
                break;
            }
        }
        
        if (columnIndex == -1) {
            throw new IllegalArgumentException("Column not found: " + columnName);
        }
        
        // Find matching rows
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            if (parts.length > columnIndex && parts[columnIndex].equals(value)) {
                results.add(parts);
            }
        }
        
        return results;
    }
    
    /**
     * Get experiment ID by type and description pattern
     */
    public String findExperimentId(String experimentType, String descriptionPattern) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(experimentsDbPath));
        
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            if (parts.length >= 4 && parts[2].equals(experimentType) && parts[3].contains(descriptionPattern)) {
                return parts[0]; // experiment_id
            }
        }
        
        return null;
    }
    
    /**
     * Get all datasets for an experiment
     */
    public List<String[]> getDatasetsByExperiment(String experimentId) throws IOException {
        return queryDatabase(datasetsDbPath, "experiment_id", experimentId);
    }
    
    /**
     * Get all models for an experiment
     */
    public List<String[]> getModelsByExperiment(String experimentId) throws IOException {
        return queryDatabase(modelsDbPath, "experiment_id", experimentId);
    }
    
    /**
     * Get all results for an experiment
     */
    public List<String[]> getResultsByExperiment(String experimentId) throws IOException {
        return queryDatabase(resultsDbPath, "experiment_id", experimentId);
    }
    
    /**
     * Get all results for a model
     */
    public List<String[]> getResultsByModel(String modelId) throws IOException {
        return queryDatabase(resultsDbPath, "model_id", modelId);
    }
    
    /**
     * Export database summary report
     */
    public void exportSummaryReport(String outputPath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("=== ERENO Experiment Tracking Database Summary ===");
            writer.println("Generated: " + DATE_FORMAT.format(new Date()));
            writer.println();
            
            // Count entries
            int expCount = Files.readAllLines(Paths.get(experimentsDbPath)).size() - 1;
            int dsCount = Files.readAllLines(Paths.get(datasetsDbPath)).size() - 1;
            int mdlCount = Files.readAllLines(Paths.get(modelsDbPath)).size() - 1;
            int resCount = Files.readAllLines(Paths.get(resultsDbPath)).size() - 1;
            
            writer.println("Total Experiments: " + expCount);
            writer.println("Total Datasets: " + dsCount);
            writer.println("Total Models: " + mdlCount);
            writer.println("Total Results: " + resCount);
            writer.println();
            
            writer.println("Database Locations:");
            writer.println("  Experiments: " + experimentsDbPath);
            writer.println("  Datasets: " + datasetsDbPath);
            writer.println("  Models: " + modelsDbPath);
            writer.println("  Results: " + resultsDbPath);
        }
        
        LOGGER.info(() -> "Exported summary report to: " + outputPath);
    }
}
