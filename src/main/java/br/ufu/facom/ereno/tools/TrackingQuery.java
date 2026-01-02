package br.ufu.facom.ereno.tools;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import br.ufu.facom.ereno.tracking.DatabaseManager;

/**
 * Command-line utility for querying and analyzing experiment tracking databases.
 * 
 * Usage:
 *   java br.ufu.facom.ereno.tools.TrackingQuery <command> [options]
 * 
 * Commands:
 *   list-experiments        - List all experiments
 *   list-datasets [expId]   - List datasets (optionally for experiment)
 *   list-models [expId]     - List models (optionally for experiment)
 *   list-results [expId]    - List results (optionally for experiment)
 *   show-experiment <id>    - Show details for an experiment
 *   show-dataset <id>       - Show details for a dataset
 *   show-model <id>         - Show details for a model
 *   show-result <id>        - Show details for a result
 *   summary                 - Generate summary report
 *   export <file>           - Export all databases to single file
 */
public class TrackingQuery {
    
    private static DatabaseManager dbManager;
    private static final String DEFAULT_DB_DIR = "target/tracking";
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        // Initialize database manager
        String dbDir = System.getProperty("tracking.dir", DEFAULT_DB_DIR);
        dbManager = new DatabaseManager(dbDir);
        
        String command = args[0].toLowerCase();
        
        try {
            switch (command) {
                case "list-experiments":
                    listExperiments();
                    break;
                    
                case "list-datasets":
                    String expIdDs = args.length > 1 ? args[1] : null;
                    listDatasets(expIdDs);
                    break;
                    
                case "list-models":
                    String expIdMdl = args.length > 1 ? args[1] : null;
                    listModels(expIdMdl);
                    break;
                    
                case "list-results":
                    String expIdRes = args.length > 1 ? args[1] : null;
                    listResults(expIdRes);
                    break;
                    
                case "show-experiment":
                    if (args.length < 2) {
                        System.err.println("Error: Missing experiment ID");
                        System.exit(1);
                    }
                    showExperiment(args[1]);
                    break;
                    
                case "show-dataset":
                    if (args.length < 2) {
                        System.err.println("Error: Missing dataset ID");
                        System.exit(1);
                    }
                    showDataset(args[1]);
                    break;
                    
                case "show-model":
                    if (args.length < 2) {
                        System.err.println("Error: Missing model ID");
                        System.exit(1);
                    }
                    showModel(args[1]);
                    break;
                    
                case "show-result":
                    if (args.length < 2) {
                        System.err.println("Error: Missing result ID");
                        System.exit(1);
                    }
                    showResult(args[1]);
                    break;
                    
                case "summary":
                    String summaryFile = args.length > 1 ? args[1] : "target/tracking/summary.txt";
                    generateSummary(summaryFile);
                    break;
                    
                case "export":
                    if (args.length < 2) {
                        System.err.println("Error: Missing output file");
                        System.exit(1);
                    }
                    exportAllDatabases(args[1]);
                    break;
                    
                case "help":
                case "--help":
                case "-h":
                    printUsage();
                    break;
                    
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("ERENO Experiment Tracking Query Tool");
        System.out.println();
        System.out.println("Usage: java br.ufu.facom.ereno.tools.TrackingQuery <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  list-experiments              List all experiments");
        System.out.println("  list-datasets [expId]         List datasets (optionally for experiment)");
        System.out.println("  list-models [expId]           List models (optionally for experiment)");
        System.out.println("  list-results [expId]          List results (optionally for experiment)");
        System.out.println("  show-experiment <id>          Show details for an experiment");
        System.out.println("  show-dataset <id>             Show details for a dataset");
        System.out.println("  show-model <id>               Show details for a model");
        System.out.println("  show-result <id>              Show details for a result");
        System.out.println("  summary [file]                Generate summary report");
        System.out.println("  export <file>                 Export all databases to single file");
        System.out.println("  help                          Show this help message");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -Dtracking.dir=<path>         Use custom tracking directory (default: target/tracking)");
    }
    
    private static void listExperiments() throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/experiments.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        
        if (lines.size() <= 1) {
            System.out.println("No experiments found.");
            return;
        }
        
        System.out.println("=== Experiments ===");
        System.out.println();
        
        // Skip header
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            if (parts.length >= 6) {
                System.out.printf("ID: %s%n", parts[0]);
                System.out.printf("  Type: %s%n", parts[2]);
                System.out.printf("  Description: %s%n", parts[3]);
                System.out.printf("  Status: %s%n", parts[5]);
                System.out.printf("  Time: %s%n", parts[1]);
                System.out.println();
            }
        }
    }
    
    private static void listDatasets(String experimentId) throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/datasets.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        
        if (lines.size() <= 1) {
            System.out.println("No datasets found.");
            return;
        }
        
        System.out.println("=== Datasets ===");
        System.out.println();
        
        int count = 0;
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            
            // Filter by experiment ID if provided
            if (experimentId != null && !parts[2].equals(experimentId)) {
                continue;
            }
            
            if (parts.length >= 9) {
                System.out.printf("ID: %s%n", parts[0]);
                System.out.printf("  Experiment: %s%n", parts[2]);
                System.out.printf("  Type: %s%n", parts[3]);
                System.out.printf("  Path: %s%n", parts[4]);
                System.out.printf("  Format: %s%n", parts[5]);
                System.out.printf("  Instances: %s%n", parts[6]);
                System.out.printf("  Attributes: %s%n", parts[7]);
                if (parts.length > 9 && !parts[9].isEmpty()) {
                    System.out.printf("  Attacks: %s%n", parts[9]);
                }
                System.out.printf("  Time: %s%n", parts[1]);
                System.out.println();
                count++;
            }
        }
        
        if (count == 0) {
            System.out.println("No datasets found" + 
                (experimentId != null ? " for experiment " + experimentId : "") + ".");
        }
    }
    
    private static void listModels(String experimentId) throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/models.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        
        if (lines.size() <= 1) {
            System.out.println("No models found.");
            return;
        }
        
        System.out.println("=== Models ===");
        System.out.println();
        
        int count = 0;
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            
            // Filter by experiment ID if provided
            if (experimentId != null && !parts[2].equals(experimentId)) {
                continue;
            }
            
            if (parts.length >= 7) {
                System.out.printf("ID: %s%n", parts[0]);
                System.out.printf("  Experiment: %s%n", parts[2]);
                System.out.printf("  Dataset: %s%n", parts[3]);
                System.out.printf("  Classifier: %s%n", parts[4]);
                System.out.printf("  Path: %s%n", parts[5]);
                System.out.printf("  Training Time: %s ms%n", parts[6]);
                System.out.printf("  Time: %s%n", parts[1]);
                System.out.println();
                count++;
            }
        }
        
        if (count == 0) {
            System.out.println("No models found" + 
                (experimentId != null ? " for experiment " + experimentId : "") + ".");
        }
    }
    
    private static void listResults(String experimentId) throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/results.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        
        if (lines.size() <= 1) {
            System.out.println("No results found.");
            return;
        }
        
        System.out.println("=== Results ===");
        System.out.println();
        
        int count = 0;
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            
            // Filter by experiment ID if provided
            if (experimentId != null && !parts[2].equals(experimentId)) {
                continue;
            }
            
            if (parts.length >= 9) {
                System.out.printf("ID: %s%n", parts[0]);
                System.out.printf("  Experiment: %s%n", parts[2]);
                System.out.printf("  Model: %s%n", parts[3]);
                System.out.printf("  Test Dataset: %s%n", parts[4]);
                System.out.printf("  Accuracy: %s%%%n", parts[5]);
                System.out.printf("  Precision: %s%n", parts[6]);
                System.out.printf("  Recall: %s%n", parts[7]);
                System.out.printf("  F1 Score: %s%n", parts[8]);
                System.out.printf("  Time: %s%n", parts[1]);
                System.out.println();
                count++;
            }
        }
        
        if (count == 0) {
            System.out.println("No results found" + 
                (experimentId != null ? " for experiment " + experimentId : "") + ".");
        }
    }
    
    private static void showExperiment(String experimentId) throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/experiments.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        
        // Find experiment
        String[] experiment = null;
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            if (parts[0].equals(experimentId)) {
                experiment = parts;
                break;
            }
        }
        
        if (experiment == null) {
            System.out.println("Experiment not found: " + experimentId);
            return;
        }
        
        // Print experiment details
        System.out.println("=== Experiment Details ===");
        System.out.println();
        System.out.println("ID: " + experiment[0]);
        System.out.println("Timestamp: " + experiment[1]);
        System.out.println("Type: " + experiment[2]);
        System.out.println("Description: " + experiment[3]);
        System.out.println("Config Path: " + experiment[4]);
        System.out.println("Status: " + experiment[5]);
        if (experiment.length > 6) {
            System.out.println("Notes: " + experiment[6]);
        }
        System.out.println();
        
        // Show related datasets
        List<String[]> datasets = dbManager.getDatasetsByExperiment(experimentId);
        System.out.println("Datasets: " + datasets.size());
        for (String[] ds : datasets) {
            System.out.println("  - " + ds[0] + " (" + ds[3] + ")");
        }
        System.out.println();
        
        // Show related models
        List<String[]> models = dbManager.getModelsByExperiment(experimentId);
        System.out.println("Models: " + models.size());
        for (String[] mdl : models) {
            System.out.println("  - " + mdl[0] + " (" + mdl[4] + ")");
        }
        System.out.println();
        
        // Show related results
        List<String[]> results = dbManager.getResultsByExperiment(experimentId);
        System.out.println("Results: " + results.size());
        for (String[] res : results) {
            System.out.println("  - " + res[0] + " (Accuracy: " + res[5] + "%)");
        }
    }
    
    private static void showDataset(String datasetId) throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/datasets.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        String[] headers = lines.get(0).split(",", -1);
        
        // Find dataset
        String[] dataset = null;
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            if (parts[0].equals(datasetId)) {
                dataset = parts;
                break;
            }
        }
        
        if (dataset == null) {
            System.out.println("Dataset not found: " + datasetId);
            return;
        }
        
        // Print dataset details
        System.out.println("=== Dataset Details ===");
        System.out.println();
        for (int i = 0; i < Math.min(headers.length, dataset.length); i++) {
            System.out.println(headers[i] + ": " + dataset[i]);
        }
    }
    
    private static void showModel(String modelId) throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/models.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        String[] headers = lines.get(0).split(",", -1);
        
        // Find model
        String[] model = null;
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            if (parts[0].equals(modelId)) {
                model = parts;
                break;
            }
        }
        
        if (model == null) {
            System.out.println("Model not found: " + modelId);
            return;
        }
        
        // Print model details
        System.out.println("=== Model Details ===");
        System.out.println();
        for (int i = 0; i < Math.min(headers.length, model.length); i++) {
            System.out.println(headers[i] + ": " + model[i]);
        }
        System.out.println();
        
        // Show results for this model
        List<String[]> results = dbManager.getResultsByModel(modelId);
        System.out.println("Evaluation Results: " + results.size());
        for (String[] res : results) {
            System.out.println("  - " + res[0] + " (Accuracy: " + res[5] + "%)");
        }
    }
    
    private static void showResult(String resultId) throws IOException {
        String dbPath = DEFAULT_DB_DIR + "/results.csv";
        List<String> lines = Files.readAllLines(Paths.get(dbPath));
        String[] headers = lines.get(0).split(",", -1);
        
        // Find result
        String[] result = null;
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            if (parts[0].equals(resultId)) {
                result = parts;
                break;
            }
        }
        
        if (result == null) {
            System.out.println("Result not found: " + resultId);
            return;
        }
        
        // Print result details
        System.out.println("=== Result Details ===");
        System.out.println();
        for (int i = 0; i < Math.min(headers.length, result.length); i++) {
            System.out.println(headers[i] + ": " + result[i]);
        }
    }
    
    private static void generateSummary(String outputFile) throws IOException {
        dbManager.exportSummaryReport(outputFile);
        System.out.println("Summary report generated: " + outputFile);
        
        // Print summary to console as well
        List<String> lines = Files.readAllLines(Paths.get(outputFile));
        for (String line : lines) {
            System.out.println(line);
        }
    }
    
    private static void exportAllDatabases(String outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            String dbDir = DEFAULT_DB_DIR;
            
            // Export experiments
            writer.println("=== EXPERIMENTS ===");
            writer.println();
            List<String> expLines = Files.readAllLines(Paths.get(dbDir + "/experiments.csv"));
            for (String line : expLines) {
                writer.println(line);
            }
            writer.println();
            writer.println();
            
            // Export datasets
            writer.println("=== DATASETS ===");
            writer.println();
            List<String> dsLines = Files.readAllLines(Paths.get(dbDir + "/datasets.csv"));
            for (String line : dsLines) {
                writer.println(line);
            }
            writer.println();
            writer.println();
            
            // Export models
            writer.println("=== MODELS ===");
            writer.println();
            List<String> mdlLines = Files.readAllLines(Paths.get(dbDir + "/models.csv"));
            for (String line : mdlLines) {
                writer.println(line);
            }
            writer.println();
            writer.println();
            
            // Export results
            writer.println("=== RESULTS ===");
            writer.println();
            List<String> resLines = Files.readAllLines(Paths.get(dbDir + "/results.csv"));
            for (String line : resLines) {
                writer.println(line);
            }
        }
        
        System.out.println("All databases exported to: " + outputFile);
    }
}
