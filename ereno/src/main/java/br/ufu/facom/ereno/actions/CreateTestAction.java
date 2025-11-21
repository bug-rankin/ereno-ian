package br.ufu.facom.ereno.actions;

import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.CSVWritter;
import br.ufu.facom.ereno.evaluation.support.GenericEvaluation;
import br.ufu.facom.ereno.evaluation.support.GenericResultado;
import br.ufu.facom.ereno.SubstationNetwork;
import br.ufu.facom.ereno.util.Labels;
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
import weka.core.converters.ArffSaver;
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
 * Action handler for creating test datasets and evaluating them.
 * 
 * This action:
 * 1. Loads benign data and training dataset
 * 2. Generates test dataset (similar structure to training)
 * 3. Trains specified classifiers on training data
 * 4. Evaluates classifiers on test data
 * 5. Outputs evaluation results
 */
public class CreateTestAction {
    
    private static final Logger LOGGER = Logger.getLogger(CreateTestAction.class.getName());
    
    public static class Config {
        public String action;
        public InputConfig input;
        public OutputConfig output;
        public DatasetStructureConfig datasetStructure;
        public List<CreateTrainingAction.Config.AttackSegmentConfig> attackSegments;
        public EvaluationConfig evaluation;
        public DevicesConfig devices;
        
        public static class DevicesConfig {
            public boolean useCVariants = false;
        }
        
        public static class InputConfig {
            public String benignDataPath;
            public String trainingDatasetPath;
            public boolean verifyBenignData = true;
            public boolean verifyTrainingData = true;
        }
        
        public static class OutputConfig {
            public String testDatasetDirectory;
            public String testDatasetFilename;
            public String evaluationOutputDirectory;
            public String evaluationOutputFilename;
        }
        
        public static class DatasetStructureConfig {
            public int messagesPerSegment = 500;
            public boolean includeBenignSegment = true;
            public boolean shuffleSegments = false;
        }
        
        public static class EvaluationConfig {
            public boolean enabled = true;
            public List<String> classifiers = Arrays.asList("J48", "RandomForest", "NaiveBayes");
            public List<String> outputMetrics = Arrays.asList("accuracy", "precision", "recall", "f1", "confusion_matrix");
            public boolean saveModel = false;
            public String modelOutputDirectory;
        }
    }
    
    public static void execute(String configPath) throws Exception {
        LOGGER.info("Starting CreateTestAction with config: " + configPath);
        
        // Load configuration
        Config config = loadConfig(configPath);
        
        // Verify input files
        if (config.input.verifyBenignData) {
            File benignFile = new File(config.input.benignDataPath);
            if (!benignFile.exists()) {
                throw new IOException("Benign data file not found: " + config.input.benignDataPath);
            }
            LOGGER.info("Verified benign data file exists");
        }
        
        if (config.input.verifyTrainingData) {
            File trainingFile = new File(config.input.trainingDatasetPath);
            if (!trainingFile.exists()) {
                throw new IOException("Training dataset file not found: " + config.input.trainingDatasetPath);
            }
            LOGGER.info("Verified training dataset file exists");
        }
        
        // Create output directories
        File testDatasetDir = new File(config.output.testDatasetDirectory);
        if (!testDatasetDir.exists()) {
            testDatasetDir.mkdirs();
            LOGGER.info("Created test dataset directory");
        }
        
        File evalDir = new File(config.output.evaluationOutputDirectory);
        if (!evalDir.exists()) {
            evalDir.mkdirs();
            LOGGER.info("Created evaluation output directory");
        }
        
        // Generate test dataset using the same logic as CreateTrainingAction
        String testDatasetPath = generateTestDataset(config);
        LOGGER.info("Test dataset created: " + testDatasetPath);
        
        // Evaluate if enabled
        if (config.evaluation.enabled) {
            evaluateDataset(config, testDatasetPath);
        } else {
            LOGGER.info("Evaluation disabled, skipping");
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
        }
        return Labels.LABELS[0]; // default to "normal"
    }

    private static Config loadConfig(String path) throws IOException {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Config.class);
        }
    }
    
    private static String generateTestDataset(Config config) throws Exception {
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
        String outputPath = new File(config.output.testDatasetDirectory, 
            config.output.testDatasetFilename).getAbsolutePath();
        
        // Determine format from filename
        boolean csvMode = outputPath.toLowerCase().endsWith(".csv");
        
        // Start writing
        if (csvMode) {
            CSVWritter.startWriting(outputPath);
        } else {
            startWriting(outputPath);
            write("@relation ereno_test");
        }
        
        // Collect all segments
        List<CreateTrainingAction.SegmentData> segments = new ArrayList<>();
        
        // Add benign segment if configured
        if (config.datasetStructure.includeBenignSegment) {
            CreateTrainingAction.SegmentData benignSegment = new CreateTrainingAction.SegmentData();
            benignSegment.name = "benign";
            benignSegment.messages = extractMessages(benignIED, config.datasetStructure.messagesPerSegment);
            
            // Ensure all benign messages have the correct label
            for (br.ufu.facom.ereno.messages.Goose message : benignSegment.messages) {
                message.setLabel(Labels.LABELS[0]); // "normal"
            }
            
            segments.add(benignSegment);
            LOGGER.info("Added benign segment with " + benignSegment.messages.size() + " messages");
        }
        
        // Generate attack segments (reuse CreateTrainingAction logic)
        for (CreateTrainingAction.Config.AttackSegmentConfig attackSegment : config.attackSegments) {
            if (!attackSegment.enabled) {
                LOGGER.info("Skipping disabled attack segment: " + attackSegment.name);
                continue;
            }
            
            if (attackSegment.attacks != null && !attackSegment.attacks.isEmpty()) {
                LOGGER.info("Generating combination attack segment: " + attackSegment.name);
                CreateTrainingAction.SegmentData segment = CreateTrainingAction.generateCombinationSegment(
                    attackSegment, benignIED, config.datasetStructure.messagesPerSegment, 
                    config.devices != null && config.devices.useCVariants);
                
                // Ensure all messages in this segment have the correct label
                String segmentLabel = getLabelForSegmentName(segment.name);
                for (br.ufu.facom.ereno.messages.Goose message : segment.messages) {
                    message.setLabel(segmentLabel);
                }
                
                segments.add(segment);
            } else {
                LOGGER.info("Generating attack segment: " + attackSegment.name);
                CreateTrainingAction.SegmentData segment = CreateTrainingAction.generateAttackSegment(
                    attackSegment, benignIED, config.datasetStructure.messagesPerSegment, 
                    config.devices != null && config.devices.useCVariants);
                
                // Ensure all messages in this segment have the correct label
                String segmentLabel = getLabelForSegmentName(segment.name);
                for (br.ufu.facom.ereno.messages.Goose message : segment.messages) {
                    message.setLabel(segmentLabel);
                }
                
                segments.add(segment);
            }
        }
        
        // Shuffle if configured
        if (config.datasetStructure.shuffleSegments) {
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
        
        LOGGER.info("Test dataset created with " + totalMessages + " messages across " + segments.size() + " segments");
        
        return outputPath;
    }
    
    private static void evaluateDataset(Config config, String testDatasetPath) throws Exception {
        LOGGER.info("Starting evaluation");
        
        // Disable Weka GUI initialization to prevent ClassCastException popup
        System.setProperty("weka.gui.GenericPropertiesCreator.useDynamic", "false");
        System.setProperty("weka.gui.GenericObjectEditor.useDynamic", "false");
        
        // Load training and test datasets
        LOGGER.info("Loading training dataset: " + config.input.trainingDatasetPath);
        DataSource trainSource = new DataSource(config.input.trainingDatasetPath);
        Instances trainData = trainSource.getDataSet();
        if (trainData.classIndex() == -1) {
            trainData.setClassIndex(trainData.numAttributes() - 1);
        }
        LOGGER.info("Loaded training data: " + trainData.numInstances() + " instances");
        
        LOGGER.info("Loading test dataset: " + testDatasetPath);
        DataSource testSource = new DataSource(testDatasetPath);
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
                
                // Evaluate using GenericEvaluation for consistency with existing code
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
        String outputPath = new File(config.output.evaluationOutputDirectory, 
            config.output.evaluationOutputFilename).getAbsolutePath();
        writeEvaluationResults(results, outputPath, config);
        
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
                config.evaluation.modelOutputDirectory : config.output.evaluationOutputDirectory);
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
            Config config) throws IOException {
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Build output structure
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("timestamp", new Date().toString());
        output.put("trainingDataset", config.input.trainingDatasetPath);
        output.put("testDataset", new File(config.output.testDatasetDirectory, 
            config.output.testDatasetFilename).getAbsolutePath());
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
