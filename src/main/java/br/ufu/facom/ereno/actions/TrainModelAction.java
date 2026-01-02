package br.ufu.facom.ereno.actions;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import br.ufu.facom.ereno.tracking.ExperimentTracker;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ConverterUtils.DataSource;

/**
 * Action handler for training machine learning models.
 * 
 * This action:
 * 1. Loads a training dataset (ARFF/CSV)
 * 2. Trains specified classifiers
 * 3. Serializes trained models to disk
 * 4. Outputs training metadata
 */
public class TrainModelAction {
    
    private static final Logger LOGGER = Logger.getLogger(TrainModelAction.class.getName());
    
    public static class Config {
        public String action;
        public InputConfig input;
        public OutputConfig output;
        public List<String> classifiers = Arrays.asList("J48", "RandomForest", "NaiveBayes");
        public ClassifierParameters classifierParameters;
        
        public static class InputConfig {
            public String trainingDatasetPath;
            public boolean verifyDataset = true;
        }
        
        public static class OutputConfig {
            public String modelDirectory = "target/models";
            public boolean saveMetadata = true;
            public String metadataFilename = "training_metadata.json";
            public boolean enableTracking = true;
            public String experimentId; // Optional: link to existing experiment
            public String trainingDatasetId; // Optional: link to tracked dataset
        }
        
        public static class ClassifierParameters {
            public J48Parameters j48;
            public RandomForestParameters randomForest;
            public IBkParameters ibk;
            
            public static class J48Parameters {
                public double confidenceFactor = 0.25;
                public int minNumObj = 2;
            }
            
            public static class RandomForestParameters {
                public int numIterations = 100;
                public int numFeatures = 0; // 0 = auto
            }
            
            public static class IBkParameters {
                public int k = 1;
            }
        }
    }
    
    public static class TrainingMetadata {
        public Date trainingDate;
        public String trainingDatasetPath;
        public int numInstances;
        public int numAttributes;
        public Map<String, ModelMetadata> models = new LinkedHashMap<>();
        
        public static class ModelMetadata {
            public String classifierName;
            public String modelPath;
            public long trainingTimeMs;
            public Date timestamp;
            public Map<String, Object> parameters = new HashMap<>();
        }
    }
    
    public static void execute(String configPath) throws Exception {
        LOGGER.info("=== Starting Train Model Action ===");
        
        // Load configuration
        Config config = loadConfig(configPath);
        
        // Verify training dataset exists
        if (config.input.verifyDataset) {
            File datasetFile = new File(config.input.trainingDatasetPath);
            if (!datasetFile.exists()) {
                throw new IOException("Training dataset not found: " + config.input.trainingDatasetPath);
            }
            LOGGER.info(() -> "Verified training dataset exists: " + config.input.trainingDatasetPath);
        }
        
        // Create output directory
        File modelDir = new File(config.output.modelDirectory);
        if (!modelDir.exists()) {
            modelDir.mkdirs();
            LOGGER.info(() -> "Created model directory: " + config.output.modelDirectory);
        }
        
        // Load training dataset
        LOGGER.info(() -> "Loading training dataset: " + config.input.trainingDatasetPath);
        DataSource source = new DataSource(config.input.trainingDatasetPath);
        Instances trainData = source.getDataSet();
        if (trainData.classIndex() == -1) {
            trainData.setClassIndex(trainData.numAttributes() - 1);
        }
        LOGGER.info(() -> "Loaded training data: " + trainData.numInstances() + " instances, " + 
            trainData.numAttributes() + " attributes");
        
        // Initialize metadata
        TrainingMetadata metadata = new TrainingMetadata();
        metadata.trainingDate = new Date();
        metadata.trainingDatasetPath = config.input.trainingDatasetPath;
        metadata.numInstances = trainData.numInstances();
        metadata.numAttributes = trainData.numAttributes();
        
        // Train each classifier
        for (String classifierName : config.classifiers) {
            LOGGER.info(() -> "Training classifier: " + classifierName);
            
            try {
                // Create classifier
                Classifier classifier = createClassifier(classifierName, config.classifierParameters);
                
                // Train
                long trainStart = System.currentTimeMillis();
                classifier.buildClassifier(trainData);
                long trainEnd = System.currentTimeMillis();
                long trainingTime = trainEnd - trainStart;
                
                LOGGER.info(() -> classifierName + " training completed in " + trainingTime + "ms");
                
                // Save model
                String modelFilename = classifierName + "_model.model";
                String modelPath = new File(config.output.modelDirectory, modelFilename).getAbsolutePath();
                SerializationHelper.write(modelPath, classifier);
                LOGGER.info(() -> "Model saved to: " + modelPath);
                
                // Store metadata
                TrainingMetadata.ModelMetadata modelMeta = new TrainingMetadata.ModelMetadata();
                modelMeta.classifierName = classifierName;
                modelMeta.modelPath = modelPath;
                modelMeta.trainingTimeMs = trainingTime;
                modelMeta.timestamp = new Date();
                modelMeta.parameters = getClassifierParameters(classifier);
                metadata.models.put(classifierName, modelMeta);
                
                // Track model if enabled
                if (config.output.enableTracking) {
                    trackModelTraining(config, classifierName, modelPath, trainingTime, 
                                      modelMeta.parameters, configPath);
                }
                
            } catch (Exception e) {
                LOGGER.severe(() -> "Failed to train classifier " + classifierName + ": " + e.getMessage());
                throw e;
            }
        }
        
        // Save metadata if configured
        if (config.output.saveMetadata) {
            String metadataPath = new File(config.output.modelDirectory, 
                config.output.metadataFilename).getAbsolutePath();
            saveMetadata(metadata, metadataPath);
            LOGGER.info(() -> "Training metadata saved to: " + metadataPath);
        }
        
        LOGGER.info("=== Train Model Action Completed Successfully ===");
        LOGGER.info(() -> "Trained " + metadata.models.size() + " classifiers");
    }
    
    private static Config loadConfig(String path) throws IOException {
        try (FileReader reader = new FileReader(path)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Config.class);
        }
    }
    
    private static Classifier createClassifier(String name, Config.ClassifierParameters params) {
        switch (name.toUpperCase()) {
            case "J48":
                J48 j48 = new J48();
                if (params != null && params.j48 != null) {
                    j48.setConfidenceFactor((float) params.j48.confidenceFactor);
                    j48.setMinNumObj(params.j48.minNumObj);
                }
                return j48;
                
            case "RANDOMFOREST":
                RandomForest rf = new RandomForest();
                if (params != null && params.randomForest != null) {
                    rf.setNumIterations(params.randomForest.numIterations);
                    rf.setNumFeatures(params.randomForest.numFeatures);
                }
                return rf;
                
            case "NAIVEBAYES":
                return new NaiveBayes();
                
            case "REPTREE":
                return new REPTree();
                
            case "KNN":
            case "IBK":
                IBk ibk = new IBk();
                if (params != null && params.ibk != null) {
                    ibk.setKNN(params.ibk.k);
                }
                return ibk;
                
            default:
                throw new IllegalArgumentException("Unknown classifier: " + name);
        }
    }
    
    private static Map<String, Object> getClassifierParameters(Classifier classifier) {
        Map<String, Object> parameters = new HashMap<>();
        
        if (classifier instanceof J48) {
            J48 j48 = (J48) classifier;
            parameters.put("confidenceFactor", j48.getConfidenceFactor());
            parameters.put("minNumObj", j48.getMinNumObj());
            parameters.put("unpruned", j48.getUnpruned());
        } else if (classifier instanceof RandomForest) {
            RandomForest rf = (RandomForest) classifier;
            parameters.put("numIterations", rf.getNumIterations());
            parameters.put("numFeatures", rf.getNumFeatures());
        } else if (classifier instanceof IBk) {
            IBk ibk = (IBk) classifier;
            parameters.put("k", ibk.getKNN());
        }
        
        return parameters;
    }
    
    private static void saveMetadata(TrainingMetadata metadata, String path) throws IOException {
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();
        
        try (FileWriter writer = new FileWriter(path)) {
            gson.toJson(metadata, writer);
        }
    }
    
    private static void trackModelTraining(Config config, String classifierName, String modelPath,
                                          long trainingTime, Map<String, Object> parameters,
                                          String configPath) {
        try {
            ExperimentTracker tracker = new ExperimentTracker();
            
            // Determine experiment ID
            String experimentId = config.output.experimentId;
            if (experimentId == null || experimentId.isEmpty()) {
                // Create a new standalone experiment
                experimentId = tracker.startExperiment(
                    "model_training",
                    "Train model: " + classifierName,
                    configPath,
                    "Standalone model training"
                );
            }
            
            // Track the model
            String modelId = tracker.trackModel(
                experimentId,
                config.output.trainingDatasetId,
                classifierName,
                modelPath,
                trainingTime,
                parameters,
                configPath,
                "Trained on " + config.input.trainingDatasetPath
            );
            
            LOGGER.info(() -> "Model tracked with ID: " + modelId);
            
            // Complete experiment if we created it
            if (config.output.experimentId == null || config.output.experimentId.isEmpty()) {
                tracker.completeExperiment(experimentId);
            }
            
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to track model training: " + e.getMessage());
            // Don't fail the action if tracking fails
        }
    }
}
