package br.ufu.facom.ereno.tracking;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

/**
 * High-level wrapper for experiment tracking that simplifies integration with actions.
 * Provides convenient methods for common tracking operations.
 */
public class ExperimentTracker {
    
    private static final Logger LOGGER = Logger.getLogger(ExperimentTracker.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final DatabaseManager dbManager;
    private String currentExperimentId;
    
    public ExperimentTracker() {
        this.dbManager = new DatabaseManager();
    }
    
    public ExperimentTracker(String databaseDirectory) {
        this.dbManager = new DatabaseManager(databaseDirectory);
    }
    
    /**
     * Start tracking a new experiment
     */
    public String startExperiment(String experimentType, String description, String pipelineConfigPath, String notes) {
        try {
            currentExperimentId = dbManager.logExperiment(experimentType, description, pipelineConfigPath, notes);
            LOGGER.info("Started tracking experiment: " + currentExperimentId);
            return currentExperimentId;
        } catch (IOException e) {
            LOGGER.warning("Failed to start experiment tracking: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Complete current experiment
     */
    public void completeExperiment(String experimentId) {
        try {
            dbManager.updateExperimentStatus(experimentId, "completed");
            LOGGER.info("Completed experiment: " + experimentId);
        } catch (IOException e) {
            LOGGER.warning("Failed to update experiment status: " + e.getMessage());
        }
    }
    
    /**
     * Mark experiment as failed
     */
    public void failExperiment(String experimentId, String reason) {
        try {
            dbManager.updateExperimentStatus(experimentId, "failed");
            LOGGER.warning("Marked experiment as failed: " + experimentId + " - " + reason);
        } catch (IOException e) {
            LOGGER.warning("Failed to update experiment status: " + e.getMessage());
        }
    }
    
    /**
     * Track a benign dataset creation
     */
    public String trackBenignDataset(String experimentId, String filePath, String configPath, 
                                     int numInstances, String notes) {
        try {
            // Auto-detect format
            String format = filePath.toLowerCase().endsWith(".csv") ? "csv" : "arff";
            
            DatabaseManager.DatasetEntry entry = new DatabaseManager.DatasetEntry();
            entry.experimentId = experimentId;
            entry.datasetType = "benign";
            entry.filePath = filePath;
            entry.format = format;
            entry.numInstances = numInstances;
            entry.configPath = configPath;
            entry.notes = notes;
            
            // Try to get attribute count from file
            try {
                DataSource source = new DataSource(filePath);
                Instances data = source.getDataSet();
                entry.numAttributes = data.numAttributes();
            } catch (Exception e) {
                entry.numAttributes = -1; // Unknown
                LOGGER.fine("Could not read attributes from dataset: " + e.getMessage());
            }
            
            String datasetId = dbManager.logDataset(entry);
            LOGGER.info("Tracked benign dataset: " + datasetId);
            return datasetId;
            
        } catch (IOException e) {
            LOGGER.warning("Failed to track benign dataset: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Track an attack dataset creation
     */
    public String trackAttackDataset(String experimentId, String filePath, String configPath,
                                     String attackTypes, String randomSeed, 
                                     Map<String, Object> datasetStructure, String notes) {
        try {
            String format = filePath.toLowerCase().endsWith(".csv") ? "csv" : "arff";
            
            DatabaseManager.DatasetEntry entry = new DatabaseManager.DatasetEntry();
            entry.experimentId = experimentId;
            entry.datasetType = "attack";
            entry.filePath = filePath;
            entry.format = format;
            entry.configPath = configPath;
            entry.attackTypes = attackTypes;
            entry.randomSeed = randomSeed;
            entry.datasetStructure = GSON.toJson(datasetStructure);
            entry.notes = notes;
            
            // Try to get instance and attribute count
            try {
                DataSource source = new DataSource(filePath);
                Instances data = source.getDataSet();
                entry.numInstances = data.numInstances();
                entry.numAttributes = data.numAttributes();
            } catch (Exception e) {
                entry.numInstances = -1;
                entry.numAttributes = -1;
                LOGGER.fine("Could not read dataset info: " + e.getMessage());
            }
            
            String datasetId = dbManager.logDataset(entry);
            LOGGER.info("Tracked attack dataset: " + datasetId);
            return datasetId;
            
        } catch (IOException e) {
            LOGGER.warning("Failed to track attack dataset: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Track a test dataset creation
     */
    public String trackTestDataset(String experimentId, String filePath, String configPath,
                                   String attackTypes, String notes) {
        try {
            String format = filePath.toLowerCase().endsWith(".csv") ? "csv" : "arff";
            
            DatabaseManager.DatasetEntry entry = new DatabaseManager.DatasetEntry();
            entry.experimentId = experimentId;
            entry.datasetType = "test";
            entry.filePath = filePath;
            entry.format = format;
            entry.configPath = configPath;
            entry.attackTypes = attackTypes;
            entry.notes = notes;
            
            // Try to get instance and attribute count
            try {
                DataSource source = new DataSource(filePath);
                Instances data = source.getDataSet();
                entry.numInstances = data.numInstances();
                entry.numAttributes = data.numAttributes();
            } catch (Exception e) {
                entry.numInstances = -1;
                entry.numAttributes = -1;
            }
            
            String datasetId = dbManager.logDataset(entry);
            LOGGER.info("Tracked test dataset: " + datasetId);
            return datasetId;
            
        } catch (IOException e) {
            LOGGER.warning("Failed to track test dataset: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Track a trained model
     */
    public String trackModel(String experimentId, String trainingDatasetId, String classifierName,
                            String modelPath, long trainingTimeMs, 
                            Map<String, Object> hyperparameters, String configPath, String notes) {
        try {
            DatabaseManager.ModelEntry entry = new DatabaseManager.ModelEntry();
            entry.experimentId = experimentId;
            entry.datasetId = trainingDatasetId;
            entry.classifierName = classifierName;
            entry.modelPath = modelPath;
            entry.trainingTimeMs = trainingTimeMs;
            entry.hyperparameters = GSON.toJson(hyperparameters);
            entry.configPath = configPath;
            entry.notes = notes;
            
            String modelId = dbManager.logModel(entry);
            LOGGER.info("Tracked model: " + modelId);
            return modelId;
            
        } catch (IOException e) {
            LOGGER.warning("Failed to track model: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Track an evaluation result
     */
    public String trackResult(String experimentId, String modelId, String testDatasetId,
                             double accuracy, double precision, double recall, double f1Score,
                             int tp, int tn, int fp, int fn, long evaluationTimeMs,
                             int[][] confusionMatrix, String configPath, String notes) {
        try {
            DatabaseManager.ResultEntry entry = new DatabaseManager.ResultEntry();
            entry.experimentId = experimentId;
            entry.modelId = modelId;
            entry.testDatasetId = testDatasetId;
            entry.accuracy = accuracy;
            entry.precision = precision;
            entry.recall = recall;
            entry.f1Score = f1Score;
            entry.truePositives = tp;
            entry.trueNegatives = tn;
            entry.falsePositives = fp;
            entry.falseNegatives = fn;
            entry.evaluationTimeMs = evaluationTimeMs;
            entry.confusionMatrix = GSON.toJson(confusionMatrix);
            entry.configPath = configPath;
            entry.notes = notes;
            
            String resultId = dbManager.logResult(entry);
            LOGGER.info("Tracked result: " + resultId);
            return resultId;
            
        } catch (IOException e) {
            LOGGER.warning("Failed to track result: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get the current experiment ID
     */
    public String getCurrentExperimentId() {
        return currentExperimentId;
    }
    
    /**
     * Set the current experiment ID (for resuming tracking)
     */
    public void setCurrentExperimentId(String experimentId) {
        this.currentExperimentId = experimentId;
    }
    
    /**
     * Export a summary report
     */
    public void exportSummaryReport(String outputPath) {
        try {
            dbManager.exportSummaryReport(outputPath);
        } catch (IOException e) {
            LOGGER.warning("Failed to export summary report: " + e.getMessage());
        }
    }
    
    /**
     * Get database manager for advanced queries
     */
    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }
}
