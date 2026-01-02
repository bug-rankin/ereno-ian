package br.ufu.facom.ereno.actions;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import br.ufu.facom.ereno.evaluation.support.GenericEvaluation;
import br.ufu.facom.ereno.evaluation.support.GenericResultado;
import br.ufu.facom.ereno.tracking.ExperimentTracker;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ConverterUtils.DataSource;

/**
 * Action handler for evaluating trained models.
 * 
 * This action:
 * 1. Loads pre-trained model(s) from disk
 * 2. Loads test dataset
 * 3. Evaluates each model on test data
 * 4. Outputs comprehensive evaluation metrics
 */
public class EvaluateAction {
    
    private static final Logger LOGGER = Logger.getLogger(EvaluateAction.class.getName());
    
    public static class Config {
        public String action;
        public InputConfig input;
        public OutputConfig output;
        public List<String> outputMetrics = Arrays.asList("accuracy", "precision", "recall", "f1", "confusion_matrix");
        
        public static class InputConfig {
            public List<ModelInput> models;
            public String testDatasetPath;
            public boolean verifyFiles = true;
            
            public static class ModelInput {
                public String name;
                public String modelPath;
                public String modelId; // Optional: tracked model ID for cross-referencing
            }
        }
        
        public static class OutputConfig {
            public String evaluationDirectory = "target/evaluation";
            public String evaluationFilename = "evaluation_results.json";
            public boolean generateTextReport = true;
            public String textReportFilename = "evaluation_report.txt";
            public boolean enableTracking = true;
            public String experimentId; // Optional: link to existing experiment
            public String testDatasetId; // Optional: link to tracked test dataset
        }
    }
    
    public static class EvaluationResults {
        public Date evaluationDate;
        public String testDatasetPath;
        public int numTestInstances;
        public Map<String, ModelEvaluation> modelResults = new LinkedHashMap<>();
        
        public static class ModelEvaluation {
            public String modelName;
            public String modelPath;
            public double accuracy;
            public double precision;
            public double recall;
            public double f1Score;
            public int truePositives;
            public int trueNegatives;
            public int falsePositives;
            public int falseNegatives;
            public int[][] confusionMatrix;
            public long evaluationTimeMs;
        }
    }
    
    public static void execute(String configPath) throws Exception {
        LOGGER.info("=== Starting Evaluate Action ===");
        
        // Load configuration
        Config config = loadConfig(configPath);
        
        // Verify inputs
        if (config.input.verifyFiles) {
            verifyInputs(config);
        }
        
        // Create output directory
        File evalDir = new File(config.output.evaluationDirectory);
        if (!evalDir.exists()) {
            evalDir.mkdirs();
            LOGGER.info("Created evaluation directory: " + config.output.evaluationDirectory);
        }
        
        // Load test dataset
        LOGGER.info("Loading test dataset: " + config.input.testDatasetPath);
        DataSource testSource = new DataSource(config.input.testDatasetPath);
        Instances testData = testSource.getDataSet();
        if (testData.classIndex() == -1) {
            testData.setClassIndex(testData.numAttributes() - 1);
        }
        LOGGER.info("Loaded test data: " + testData.numInstances() + " instances");
        
        // Initialize results
        EvaluationResults results = new EvaluationResults();
        results.evaluationDate = new Date();
        results.testDatasetPath = config.input.testDatasetPath;
        results.numTestInstances = testData.numInstances();
        
        // Evaluate each model
        for (Config.InputConfig.ModelInput modelInput : config.input.models) {
            LOGGER.info("Evaluating model: " + modelInput.name);
            
            try {
                // Load model
                LOGGER.info("Loading model from: " + modelInput.modelPath);
                Classifier classifier = (Classifier) SerializationHelper.read(modelInput.modelPath);
                
                // Evaluate
                long evalStart = System.currentTimeMillis();
                
                GenericResultado resultado = GenericEvaluation.runSingleClassifier(testData, testData);
                
                long evalEnd = System.currentTimeMillis();
                long evaluationTime = evalEnd - evalStart;
                
                // Build evaluation result
                EvaluationResults.ModelEvaluation modelEval = new EvaluationResults.ModelEvaluation();
                modelEval.modelName = modelInput.name;
                modelEval.modelPath = modelInput.modelPath;
                modelEval.accuracy = resultado.getAcuracia();
                modelEval.precision = resultado.getPrecision();
                modelEval.recall = resultado.getRecall();
                modelEval.f1Score = resultado.getF1Score();
                modelEval.truePositives = (int) resultado.getVP();
                modelEval.trueNegatives = (int) resultado.getVN();
                modelEval.falsePositives = (int) resultado.getFP();
                modelEval.falseNegatives = (int) resultado.getFN();
                modelEval.confusionMatrix = resultado.getConfusionMatrix();
                modelEval.evaluationTimeMs = evaluationTime;
                
                results.modelResults.put(modelInput.name, modelEval);
                
                LOGGER.info(String.format("%s - Accuracy: %.2f%%, Precision: %.4f, Recall: %.4f, F1: %.4f",
                    modelInput.name, modelEval.accuracy, modelEval.precision, 
                    modelEval.recall, modelEval.f1Score));
                
                // Track result if enabled
                if (config.output.enableTracking) {
                    trackEvaluationResult(config, modelInput, modelEval, configPath);
                }
                
            } catch (Exception e) {
                LOGGER.severe("Failed to evaluate model " + modelInput.name + ": " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }
        
        // Save results
        String jsonPath = new File(config.output.evaluationDirectory, 
            config.output.evaluationFilename).getAbsolutePath();
        saveResultsJson(results, jsonPath);
        LOGGER.info("Evaluation results saved to: " + jsonPath);
        
        // Generate text report if configured
        if (config.output.generateTextReport) {
            String textPath = new File(config.output.evaluationDirectory, 
                config.output.textReportFilename).getAbsolutePath();
            generateTextReport(results, textPath);
            LOGGER.info("Text report saved to: " + textPath);
        }
        
        LOGGER.info("=== Evaluate Action Completed Successfully ===");
        LOGGER.info("Evaluated " + results.modelResults.size() + " models");
    }
    
    private static Config loadConfig(String path) throws IOException {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Config.class);
        }
    }
    
    private static void verifyInputs(Config config) throws IOException {
        // Verify test dataset
        File testFile = new File(config.input.testDatasetPath);
        if (!testFile.exists()) {
            throw new IOException("Test dataset not found: " + config.input.testDatasetPath);
        }
        LOGGER.info("Verified test dataset exists");
        
        // Verify model files
        for (Config.InputConfig.ModelInput model : config.input.models) {
            File modelFile = new File(model.modelPath);
            if (!modelFile.exists()) {
                throw new IOException("Model file not found: " + model.modelPath);
            }
            LOGGER.info("Verified model exists: " + model.name);
        }
    }
    
    private static void saveResultsJson(EvaluationResults results, String path) throws IOException {
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();
        
        try (FileWriter writer = new FileWriter(path)) {
            gson.toJson(results, writer);
        }
    }
    
    private static void generateTextReport(EvaluationResults results, String path) throws IOException {
        StringBuilder report = new StringBuilder();
        
        report.append("===============================================\n");
        report.append("        MODEL EVALUATION REPORT\n");
        report.append("===============================================\n\n");
        report.append("Evaluation Date: ").append(results.evaluationDate).append("\n");
        report.append("Test Dataset: ").append(results.testDatasetPath).append("\n");
        report.append("Test Instances: ").append(results.numTestInstances).append("\n");
        report.append("Models Evaluated: ").append(results.modelResults.size()).append("\n\n");
        
        report.append("===============================================\n");
        report.append("        MODEL RESULTS\n");
        report.append("===============================================\n\n");
        
        for (Map.Entry<String, EvaluationResults.ModelEvaluation> entry : results.modelResults.entrySet()) {
            EvaluationResults.ModelEvaluation eval = entry.getValue();
            
            report.append("Model: ").append(eval.modelName).append("\n");
            report.append("-----------------------------------------------\n");
            report.append(String.format("Accuracy:     %.2f%%\n", eval.accuracy));
            report.append(String.format("Precision:    %.4f\n", eval.precision));
            report.append(String.format("Recall:       %.4f\n", eval.recall));
            report.append(String.format("F1 Score:     %.4f\n", eval.f1Score));
            report.append(String.format("True Pos:     %d\n", eval.truePositives));
            report.append(String.format("True Neg:     %d\n", eval.trueNegatives));
            report.append(String.format("False Pos:    %d\n", eval.falsePositives));
            report.append(String.format("False Neg:    %d\n", eval.falseNegatives));
            report.append(String.format("Eval Time:    %dms\n", eval.evaluationTimeMs));
            
            if (eval.confusionMatrix != null && eval.confusionMatrix.length > 0) {
                report.append("\nConfusion Matrix:\n");
                for (int[] row : eval.confusionMatrix) {
                    report.append("  ");
                    for (int value : row) {
                        report.append(String.format("%8d ", value));
                    }
                    report.append("\n");
                }
            }
            
            report.append("\n");
        }
        
        report.append("===============================================\n");
        
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(report.toString());
        }
    }
    
    private static void trackEvaluationResult(Config config, Config.InputConfig.ModelInput modelInput,
                                             EvaluationResults.ModelEvaluation eval, String configPath) {
        try {
            ExperimentTracker tracker = new ExperimentTracker();
            
            // Determine experiment ID
            String experimentId = config.output.experimentId;
            if (experimentId == null || experimentId.isEmpty()) {
                // Create a new standalone experiment
                experimentId = tracker.startExperiment(
                    "model_evaluation",
                    "Evaluate model: " + modelInput.name,
                    configPath,
                    "Standalone model evaluation"
                );
            }
            
            // Track the result
            String resultId = tracker.trackResult(
                experimentId,
                modelInput.modelId,
                config.output.testDatasetId,
                eval.accuracy,
                eval.precision,
                eval.recall,
                eval.f1Score,
                eval.truePositives,
                eval.trueNegatives,
                eval.falsePositives,
                eval.falseNegatives,
                eval.evaluationTimeMs,
                eval.confusionMatrix,
                configPath,
                "Evaluated " + modelInput.name + " on " + config.input.testDatasetPath
            );
            
            LOGGER.info("Result tracked with ID: " + resultId);
            
            // Complete experiment if we created it
            if (config.output.experimentId == null || config.output.experimentId.isEmpty()) {
                tracker.completeExperiment(experimentId);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Failed to track evaluation result: " + e.getMessage());
            // Don't fail the action if tracking fails
        }
    }
}
