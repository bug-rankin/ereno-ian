package br.ufu.facom.ereno.actions;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;

import br.ufu.facom.ereno.evaluation.support.GenericResultado;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ConverterUtils.DataSource;

/**
 * Comprehensive Evaluation Action
 * 
 * Evaluates multiple models against multiple test datasets and outputs results to CSV.
 * Designed for large-scale evaluation experiments with thousands of model-dataset combinations.
 */
public class ComprehensiveEvaluateAction {
    
    private static final Logger LOGGER = Logger.getLogger(ComprehensiveEvaluateAction.class.getName());
    
    public static class Config {
        public String action;
        public String description;
        public InputConfig input;
        public OutputConfig output;
        
        public static class InputConfig {
            public List<ModelSpec> models;
            public List<TestDatasetSpec> testDatasets;
            public boolean verifyFiles = true;
            
            public static class ModelSpec {
                public String trainingAttack1;
                public String trainingAttack2;
                public String trainingPattern;
                public String modelName; // J48, RandomForest, etc.
                public String modelPath;
            }
            
            public static class TestDatasetSpec {
                public String testAttack; // e.g., "uc01_random_replay" or "uc01_uc02"
                public String testDatasetPath;
            }
        }
        
        public static class OutputConfig {
            public String csvFilePath = "target/comprehensive_evaluation_results.csv";
            public boolean appendMode = false;
            public boolean includeHeaders = true;
        }
    }
    
    public static class EvaluationRow {
        public String trainingAttack1;
        public String trainingAttack2;
        public String trainingPattern;
        public String modelName;
        public String testAttack;
        public double accuracy;
        public double precision;
        public double recall;
        public double f1;
        
        public String toCsvRow() {
            // Replace NaN with 0.0 for cleaner CSV output
            double cleanAccuracy = Double.isNaN(accuracy) ? 0.0 : accuracy;
            double cleanPrecision = Double.isNaN(precision) ? 0.0 : precision;
            double cleanRecall = Double.isNaN(recall) ? 0.0 : recall;
            double cleanF1 = Double.isNaN(f1) ? 0.0 : f1;
            
            return String.format("%s,%s,%s,%s,%s,%.4f,%.4f,%.4f,%.4f",
                trainingAttack1,
                trainingAttack2,
                trainingPattern,
                modelName,
                testAttack,
                cleanAccuracy,
                cleanPrecision,
                cleanRecall,
                cleanF1);
        }
    }
    
    public static void execute(String configPath) throws Exception {
        LOGGER.info("=== Starting Comprehensive Evaluate Action ===");
        
        // Load configuration
        Config config = loadConfig(configPath);
        
        // Verify inputs if requested
        if (config.input.verifyFiles) {
            verifyInputs(config);
        }
        
        // Prepare output file
        File outputFile = new File(config.output.csvFilePath);
        outputFile.getParentFile().mkdirs();
        
        boolean writeHeaders = config.output.includeHeaders && (!config.output.appendMode || !outputFile.exists());
        
        // Open CSV writer
        try (PrintWriter csvWriter = new PrintWriter(new FileWriter(outputFile, config.output.appendMode))) {
            
            // Write CSV header if needed
            if (writeHeaders) {
                csvWriter.println("trainingAttack1,trainingAttack2,trainingPattern,modelName,testAttack,accuracy,precision,recall,f1");
            }
            
            int totalEvaluations = config.input.models.size() * config.input.testDatasets.size();
            int currentEvaluation = 0;
            
            LOGGER.info(String.format("Starting %d evaluations (%d models × %d test datasets)",
                totalEvaluations, config.input.models.size(), config.input.testDatasets.size()));
            
            // Evaluate each model against each test dataset
            for (Config.InputConfig.ModelSpec model : config.input.models) {
                // Load model once for all test datasets
                Classifier classifier = loadModel(model.modelPath);
                
                for (Config.InputConfig.TestDatasetSpec testDataset : config.input.testDatasets) {
                    currentEvaluation++;
                    
                    try {
                        // Load test data
                        DataSource testSource = new DataSource(testDataset.testDatasetPath);
                        Instances testData = testSource.getDataSet();
                        if (testData.classIndex() == -1) {
                            testData.setClassIndex(testData.numAttributes() - 1);
                        }
                        
                        // Evaluate model on test data
                        GenericResultado resultado = evaluateClassifier(classifier, testData);
                        
                        // Create evaluation row
                        EvaluationRow row = new EvaluationRow();
                        row.trainingAttack1 = model.trainingAttack1;
                        row.trainingAttack2 = model.trainingAttack2;
                        row.trainingPattern = model.trainingPattern;
                        row.modelName = model.modelName;
                        row.testAttack = testDataset.testAttack;
                        row.accuracy = resultado.getAcuracia();
                        row.precision = resultado.getPrecision();
                        row.recall = resultado.getRecall();
                        row.f1 = resultado.getF1Score();
                        
                        // Write to CSV
                        csvWriter.println(row.toCsvRow());
                        csvWriter.flush();
                        
                        // Log progress
                        if (currentEvaluation % 100 == 0 || currentEvaluation == totalEvaluations) {
                            LOGGER.info(String.format("Progress: %d/%d (%.1f%%) - Latest: %s/%s on %s - F1: %.4f",
                                currentEvaluation, totalEvaluations,
                                (100.0 * currentEvaluation / totalEvaluations),
                                model.trainingAttack1 + "_" + model.trainingAttack2,
                                model.modelName,
                                testDataset.testAttack,
                                row.f1));
                        }
                        
                    } catch (Exception e) {
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        LOGGER.severe(String.format("Failed evaluation %d/%d: Model=%s/%s/%s, Test=%s - %s",
                            currentEvaluation, totalEvaluations,
                            model.trainingAttack1, model.trainingAttack2, model.modelName,
                            testDataset.testAttack,
                            errorMsg));
                        if (e.getMessage() == null) {
                            e.printStackTrace(); // Print full stack trace for debugging
                        }
                        // Continue with next evaluation instead of failing completely
                    }
                }
            }
        }
        
        LOGGER.info("=== Comprehensive Evaluate Action Completed ===");
        LOGGER.info(String.format("Results saved to: %s", outputFile.getAbsolutePath()));
    }
    
    private static Config loadConfig(String path) throws IOException {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Config.class);
        }
    }
    
    private static void verifyInputs(Config config) throws IOException {
        LOGGER.info("Verifying input files...");
        
        int missingModels = 0;
        int missingDatasets = 0;
        
        // Verify model files
        for (Config.InputConfig.ModelSpec model : config.input.models) {
            File modelFile = new File(model.modelPath);
            if (!modelFile.exists()) {
                missingModels++;
                if (missingModels <= 5) { // Only log first few
                    LOGGER.warning(() -> "Model file not found: " + model.modelPath);
                }
            }
        }
        
        // Verify test dataset files
        for (Config.InputConfig.TestDatasetSpec dataset : config.input.testDatasets) {
            File datasetFile = new File(dataset.testDatasetPath);
            if (!datasetFile.exists()) {
                missingDatasets++;
                if (missingDatasets <= 5) { // Only log first few
                    LOGGER.warning(() -> "Test dataset not found: " + dataset.testDatasetPath);
                }
            }
        }
        
        if (missingModels > 0 || missingDatasets > 0) {
            throw new IOException(String.format("Missing files: %d models, %d test datasets", 
                missingModels, missingDatasets));
        }
        
        LOGGER.info(String.format("Verification complete: %d models, %d test datasets",
            config.input.models.size(), config.input.testDatasets.size()));
    }
    
    private static Classifier loadModel(String modelPath) throws Exception {
        return (Classifier) SerializationHelper.read(modelPath);
    }
    
    private static GenericResultado evaluateClassifier(Classifier classifier, Instances testData) throws Exception {
        // Use existing evaluation infrastructure
        // Note: This assumes the classifier has been trained
        // We're just evaluating it on the test data
        weka.classifiers.Evaluation eval = new weka.classifiers.Evaluation(testData);
        eval.evaluateModel(classifier, testData);
        
        // Extract confusion matrix
        double[][] confusionMatrixDouble = eval.confusionMatrix();
        int[][] confusionMatrix = new int[confusionMatrixDouble.length][confusionMatrixDouble[0].length];
        for (int i = 0; i < confusionMatrixDouble.length; i++) {
            for (int j = 0; j < confusionMatrixDouble[i].length; j++) {
                confusionMatrix[i][j] = (int) confusionMatrixDouble[i][j];
            }
        }
        
        // Verify confusion matrix size
        if (confusionMatrix.length < 2 || confusionMatrix[0].length < 2) {
            throw new IllegalStateException(String.format(
                "Confusion matrix too small: %dx%d. Expected at least 2x2 for binary classification.",
                confusionMatrix.length, confusionMatrix[0].length));
        }
        
        // Calculate metrics (assuming binary: [0]=normal, [1]=attack)
        float VP = (float) confusionMatrix[1][1];
        float VN = (float) confusionMatrix[0][0];
        float FP = (float) confusionMatrix[0][1];
        float FN = (float) confusionMatrix[1][0];
        
        // Create GenericResultado
        GenericResultado resultado = new GenericResultado("Evaluation", VP, FN, VN, FP, 0.0f, confusionMatrix);
        
        return resultado;
    }
}
