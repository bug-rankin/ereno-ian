package br.ufu.facom.ereno.actions;

import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.CSVWritter;
import br.ufu.facom.ereno.evaluation.support.GenericEvaluation;
import br.ufu.facom.ereno.evaluation.support.GenericResultado;
import br.ufu.facom.ereno.util.BenignDataManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.SerializationHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter.*;

/**
 * Action handler for combined training and testing workflow.
 * 
 * This action:
 * 1. Generates benign data if it doesn't exist
 * 2. Generates training dataset with specified attacks
 * 3. Generates test dataset (potentially with different attacks)
 * 4. Trains classifiers on training data
 * 5. Evaluates on test data
 * 6. Outputs comprehensive results
 * 
 * This is useful for complete ML pipeline execution in a single run.
 */
public class TrainAndTestAction {
    
    private static final Logger LOGGER = Logger.getLogger(TrainAndTestAction.class.getName());
    
    public static class Config {
        public String action;
        public InputConfig input;
        public OutputConfig output;
        public DatasetConfig trainingDataset;
        public DatasetConfig testDataset;
        public AttackSegmentsConfig attackSegments;
        public EvaluationConfig evaluation;
        public BenignGenerationConfig benignGeneration;
        public DevicesConfig devices;
        
        public static class InputConfig {
            public String benignDataPath;
            public boolean verifyBenignData = true;
            public boolean createBenignIfMissing = true;
        }
        
        public static class BenignGenerationConfig {
            public int numberOfMessages = 40000;
            public double faultProbability = 0.05;
            public String outputDirectory = "target/benign_data";
            public boolean saveArff = true;
            public boolean saveCsv = false;
        }
        
        public static class OutputConfig {
            public String trainingDirectory;
            public String trainingFilename;
            public String testDirectory;
            public String testFilename;
            public String evaluationDirectory;
            public String evaluationFilename;
        }
        
        public static class DatasetConfig {
            public int messagesPerSegment = 1000;
            public boolean includeBenignSegment = true;
            public boolean shuffleSegments = false;
        }
        
        public static class AttackSegmentsConfig {
            public List<CreateTrainingAction.Config.AttackSegmentConfig> training;
            public List<CreateTrainingAction.Config.AttackSegmentConfig> testing;
        }
        
        public static class EvaluationConfig {
            public boolean enabled = true;
            public List<String> classifiers = Arrays.asList("J48", "RandomForest", "NaiveBayes");
            public List<String> outputMetrics = Arrays.asList("accuracy", "precision", "recall", "f1", "confusion_matrix");
            public boolean saveModel = false;
            public String modelOutputDirectory;
        }
        
        public static class DevicesConfig {
            public boolean useLegacy = false;
        }
    }
    
    public static void execute(String configPath) throws Exception {
        LOGGER.info("Starting TrainAndTestAction with config: " + configPath);
        
        // Load configuration
        Config config = loadConfig(configPath);
        
        // Check if benign data exists, create if missing and configured to do so
        File benignFile = new File(config.input.benignDataPath);
        if (!benignFile.exists()) {
            if (config.input.createBenignIfMissing) {
                LOGGER.info("Benign data not found at: " + config.input.benignDataPath);
                LOGGER.info("Creating benign data...");
                generateBenignData(config);
                LOGGER.info("Benign data created successfully");
            } else {
                throw new IOException("Benign data file not found: " + config.input.benignDataPath);
            }
        } else if (config.input.verifyBenignData) {
            LOGGER.info("Verified benign data file exists: " + config.input.benignDataPath);
        }
        
        // Create output directories
        ensureDirectory(config.output.trainingDirectory);
        ensureDirectory(config.output.testDirectory);
        ensureDirectory(config.output.evaluationDirectory);
        
        // Load benign data once (shared between training and test)
        LOGGER.info("Loading benign data from: " + config.input.benignDataPath);
        LegitimateProtectionIED benignIED = BenignDataManager.loadBenignData(config.input.benignDataPath);
        LOGGER.info("Loaded " + benignIED.getNumberOfMessages() + " benign messages");
        
        // Generate training dataset
        LOGGER.info("=== Generating Training Dataset ===");
        String trainingPath = generateDataset(
            benignIED,
            config.output.trainingDirectory,
            config.output.trainingFilename,
            config.trainingDataset,
            config.attackSegments.training,
            "training",
            config
        );
        LOGGER.info("Training dataset created: " + trainingPath);
        
        // Generate test dataset
        LOGGER.info("=== Generating Test Dataset ===");
        String testPath = generateDataset(
            benignIED,
            config.output.testDirectory,
            config.output.testFilename,
            config.testDataset,
            config.attackSegments.testing,
            "test",
            config
        );
        LOGGER.info("Test dataset created: " + testPath);
        
        // Evaluate if enabled
        if (config.evaluation.enabled) {
            LOGGER.info("=== Starting Evaluation ===");
            evaluateDatasets(config, trainingPath, testPath);
        } else {
            LOGGER.info("Evaluation disabled, skipping");
        }
        
        LOGGER.info("TrainAndTestAction completed successfully");
    }
    
    private static void generateBenignData(Config config) throws Exception {
        // Use benignGeneration config if available, otherwise use defaults
        int numberOfMessages = 40000;
        double faultProbability = 0.05;
        String outputDirectory = "target/benign_data";
        boolean saveArff = true;
        boolean saveCsv = false;
        
        if (config.benignGeneration != null) {
            numberOfMessages = config.benignGeneration.numberOfMessages;
            faultProbability = config.benignGeneration.faultProbability;
            outputDirectory = config.benignGeneration.outputDirectory;
            saveArff = config.benignGeneration.saveArff;
            saveCsv = config.benignGeneration.saveCsv;
        }
        
        LOGGER.info(String.format("Generating %d benign messages with %.1f%% fault probability", 
            numberOfMessages, faultProbability * 100));
        
        // Set fault probability in ConfigLoader
        ConfigLoader.benignData.faultProbability = (int) (faultProbability * 100);
        ConfigLoader.benignData.benignDataDir = outputDirectory;
        
        // Generate benign traffic
        LegitimateProtectionIED benignIED = new LegitimateProtectionIED();
        benignIED.run(numberOfMessages);
        
        LOGGER.info("Generated " + benignIED.getNumberOfMessages() + " benign messages");
        
        // Ensure output directory exists
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Save in ARFF format
        if (saveArff) {
            LOGGER.info("Saving benign data in ARFF format...");
            BenignDataManager.saveBenignData(benignIED.copyMessages(), "arff");
            LOGGER.info("Saved to: " + config.input.benignDataPath);
        }
        
        // Save in CSV format if configured
        if (saveCsv) {
            LOGGER.info("Saving benign data in CSV format...");
            BenignDataManager.saveBenignData(benignIED.copyMessages(), "csv");
            String csvPath = BenignDataManager.getBenignDataPath("csv");
            LOGGER.info("Saved to: " + csvPath);
        }
    }
    
    private static Config loadConfig(String path) throws IOException {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Config.class);
        }
    }
    
    private static void ensureDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
            LOGGER.info("Created directory: " + path);
        }
    }
    
    private static String generateDataset(
            LegitimateProtectionIED benignIED,
            String outputDirectory,
            String outputFilename,
            Config.DatasetConfig datasetConfig,
            List<CreateTrainingAction.Config.AttackSegmentConfig> attackSegments,
            String datasetType,
            Config config) throws Exception {
        
        // Prepare output file
        String outputPath = new File(outputDirectory, outputFilename).getAbsolutePath();
        boolean csvMode = outputPath.toLowerCase().endsWith(".csv");
        
        // Start writing
        if (csvMode) {
            CSVWritter.startWriting(outputPath);
        } else {
            startWriting(outputPath);
            write("@relation ereno_" + datasetType);
        }
        
        // Collect all segments
        List<CreateTrainingAction.SegmentData> segments = new ArrayList<>();
        
        // Add benign segment if configured
        if (datasetConfig.includeBenignSegment) {
            CreateTrainingAction.SegmentData benignSegment = new CreateTrainingAction.SegmentData();
            benignSegment.name = "benign";
            benignSegment.messages = extractMessages(benignIED, datasetConfig.messagesPerSegment);
            
            // Set labels for benign messages
            String benignLabel = getLabelForSegmentName("benign");
            for (br.ufu.facom.ereno.messages.Goose message : benignSegment.messages) {
                message.setLabel(benignLabel);
            }
            
            segments.add(benignSegment);
            LOGGER.info("Added benign segment with " + benignSegment.messages.size() + " messages");
        }
        
        // Generate attack segments
        for (CreateTrainingAction.Config.AttackSegmentConfig attackSegment : attackSegments) {
            if (!attackSegment.enabled) {
                LOGGER.info("Skipping disabled attack segment: " + attackSegment.name);
                continue;
            }
            
            if (attackSegment.attacks != null && !attackSegment.attacks.isEmpty()) {
                LOGGER.info("Generating combination attack segment: " + attackSegment.name);
                boolean useLegacy = (config.devices != null && config.devices.useLegacy);
                CreateTrainingAction.SegmentData segment = CreateTrainingAction.generateCombinationSegment(
                    attackSegment, benignIED, datasetConfig.messagesPerSegment, useLegacy);
                
                // Set labels for all messages in this segment
                String segmentLabel = getLabelForSegmentName(segment.name);
                for (br.ufu.facom.ereno.messages.Goose message : segment.messages) {
                    message.setLabel(segmentLabel);
                }
                
                segments.add(segment);
            } else {
                LOGGER.info("Generating attack segment: " + attackSegment.name);
                boolean useLegacy = (config.devices != null && config.devices.useLegacy);
                CreateTrainingAction.SegmentData segment = CreateTrainingAction.generateAttackSegment(
                    attackSegment, benignIED, datasetConfig.messagesPerSegment, useLegacy);
                
                // Set labels for all messages in this segment
                String segmentLabel = getLabelForSegmentName(segment.name);
                for (br.ufu.facom.ereno.messages.Goose message : segment.messages) {
                    message.setLabel(segmentLabel);
                }
                
                segments.add(segment);
            }
        }
        
        // Shuffle if configured
        if (datasetConfig.shuffleSegments) {
            Collections.shuffle(segments, ConfigLoader.RNG);
            LOGGER.info("Shuffled segment order");
        }
        
        // Write segments
        int totalMessages = 0;
        boolean isFirstSegment = true;
        for (CreateTrainingAction.SegmentData segment : segments) {
            LOGGER.info("Writing segment '" + segment.name + "' with " + segment.messages.size() + " messages");
            
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
        
        LOGGER.info(datasetType + " dataset: " + totalMessages + " messages across " + segments.size() + " segments");
        
        return outputPath;
    }
    
    private static String getLabelForSegmentName(String segmentName) {
        // Map segment names to label values
        if (segmentName.equals("benign")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[0]; // "normal"
        } else if (segmentName.startsWith("uc01")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[1]; // "random_replay"
        } else if (segmentName.startsWith("uc02")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[2]; // "inverse_replay"
        } else if (segmentName.startsWith("uc03")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[3]; // "masquerade_fake_fault"
        } else if (segmentName.startsWith("uc04")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[4]; // "masquerade_fake_normal"
        } else if (segmentName.startsWith("uc05")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[5]; // "injection"
        } else if (segmentName.startsWith("uc06")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[6]; // "high_StNum"
        } else if (segmentName.startsWith("uc07")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[7]; // "poisoned_high_rate"
        } else if (segmentName.startsWith("uc08")) {
            return br.ufu.facom.ereno.util.Labels.LABELS[8]; // "grayhole"
        }
        return br.ufu.facom.ereno.util.Labels.LABELS[0]; // default to "normal"
    }
    
    private static void evaluateDatasets(Config config, String trainingPath, String testPath) throws Exception {
        // Disable Weka GUI initialization to prevent ClassCastException popup
        System.setProperty("weka.gui.GenericPropertiesCreator.useDynamic", "false");
        System.setProperty("weka.gui.GenericObjectEditor.useDynamic", "false");
        
        // Load datasets
        LOGGER.info("Loading training dataset: " + trainingPath);
        DataSource trainSource = new DataSource(trainingPath);
        Instances trainData = trainSource.getDataSet();
        if (trainData.classIndex() == -1) {
            trainData.setClassIndex(trainData.numAttributes() - 1);
        }
        LOGGER.info("Loaded training data: " + trainData.numInstances() + " instances");
        
        LOGGER.info("Loading test dataset: " + testPath);
        DataSource testSource = new DataSource(testPath);
        Instances testData = testSource.getDataSet();
        if (testData.classIndex() == -1) {
            testData.setClassIndex(testData.numAttributes() - 1);
        }
        LOGGER.info("Loaded test data: " + testData.numInstances() + " instances");
        
        // Evaluate each classifier
        Map<String, EvaluationResult> results = new LinkedHashMap<>();
        
        for (String classifierName : config.evaluation.classifiers) {
            LOGGER.info("Evaluating classifier: " + classifierName);
            
            try {
                // Create and train classifier
                Classifier classifier = createClassifier(classifierName);
                long trainStart = System.currentTimeMillis();
                classifier.buildClassifier(trainData);
                long trainEnd = System.currentTimeMillis();
                
                // Evaluate
                GenericResultado resultado;
                if ("J48".equalsIgnoreCase(classifierName)) {
                    resultado = GenericEvaluation.runSingleClassifierJ48(trainData, testData);
                } else {
                    resultado = GenericEvaluation.runSingleClassifier(trainData, testData);
                }
                
                // Build result
                EvaluationResult result = new EvaluationResult();
                result.classifierName = classifierName;
                result.trainingTimeMs = trainEnd - trainStart;
                result.accuracy = resultado.getAcuracia();
                result.precision = resultado.getPrecision();
                result.recall = resultado.getRecall();
                result.f1Score = resultado.getF1Score();
                result.truePositives = (int) resultado.getVP();
                result.trueNegatives = (int) resultado.getVN();
                result.falsePositives = (int) resultado.getFP();
                result.falseNegatives = (int) resultado.getFN();
                result.confusionMatrix = resultado.getConfusionMatrix();
                
                results.put(classifierName, result);
                
                LOGGER.info(String.format("%s - Accuracy: %.2f%%, F1: %.4f", 
                    classifierName, result.accuracy, result.f1Score));
                
                // Save model if configured
                if (config.evaluation.saveModel) {
                    saveModel(classifier, classifierName, config);
                }
                
            } catch (Exception e) {
                LOGGER.severe("Failed to evaluate classifier " + classifierName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Write evaluation results
        String outputPath = new File(config.output.evaluationDirectory, 
            config.output.evaluationFilename).getAbsolutePath();
        writeEvaluationResults(results, outputPath, trainingPath, testPath);
        
        LOGGER.info("Evaluation complete, results written to: " + outputPath);
    }
    
    private static Classifier createClassifier(String name) {
        switch (name.toUpperCase()) {
            case "J48":
                return new J48();
            case "RANDOMFOREST":
                return new RandomForest();
            case "NAIVEBAYES":
                return new NaiveBayes();
            case "REPTREE":
                return new REPTree();
            case "KNN":
            case "IBK":
                return new IBk();
            default:
                LOGGER.warning("Unknown classifier: " + name + ", using J48");
                return new J48();
        }
    }
    
    private static void saveModel(Classifier classifier, String classifierName, Config config) {
        try {
            File modelDir = new File(config.evaluation.modelOutputDirectory != null ? 
                config.evaluation.modelOutputDirectory : config.output.evaluationDirectory);
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }
            
            String modelPath = new File(modelDir, classifierName + "_model.model").getAbsolutePath();
            SerializationHelper.write(modelPath, classifier);
            LOGGER.info("Saved model: " + modelPath);
        } catch (Exception e) {
            LOGGER.severe("Failed to save model for " + classifierName + ": " + e.getMessage());
        }
    }
    
    private static void writeEvaluationResults(
            Map<String, EvaluationResult> results, 
            String outputPath,
            String trainingPath,
            String testPath) throws IOException {
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Build output structure
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("timestamp", new Date().toString());
        output.put("trainingDataset", trainingPath);
        output.put("testDataset", testPath);
        output.put("results", results);
        
        // Write JSON
        try (FileWriter writer = new FileWriter(outputPath)) {
            gson.toJson(output, writer);
        }
    }
    
    private static ArrayList<br.ufu.facom.ereno.messages.Goose> extractMessages(
            LegitimateProtectionIED ied, int count) {
        ArrayList<br.ufu.facom.ereno.messages.Goose> messages = ied.copyMessages();
        if (messages.size() > count) {
            return new ArrayList<>(messages.subList(0, count));
        }
        return messages;
    }
    
    public static class EvaluationResult {
        public String classifierName;
        public long trainingTimeMs;
        public double accuracy;
        public double precision;
        public double recall;
        public double f1Score;
        public int truePositives;
        public int trueNegatives;
        public int falsePositives;
        public int falseNegatives;
        public int[][] confusionMatrix;
    }
}
