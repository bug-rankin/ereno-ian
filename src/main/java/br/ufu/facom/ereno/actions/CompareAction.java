package br.ufu.facom.ereno.actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import br.ufu.facom.ereno.config.ActionConfigLoader;
import br.ufu.facom.ereno.messages.Goose;

/**
 * Action runner for comparing benign data with attack datasets.
 * Analyzes field changes, burst patterns, timing anomalies, and sequence violations.
 */
public class CompareAction {

    private static final Logger LOGGER = Logger.getLogger(CompareAction.class.getName());

    public static class Config {
        public String action;
        public String description;
        public InputConfig input;
        public OutputConfig output;
        public ComparisonMetrics comparisonMetrics;
        public ComparisonSettings comparison;

        public static class InputConfig {
            public String benignDataPath;
            public String attackDatasetPath;
            public boolean verifyFiles = true;
        }

        public static class OutputConfig {
            public String directory = "target/comparison";
            public String filename = "comparison_report.json";
            public boolean generateVisualizations = false;
            public String visualizationDirectory;
            public boolean generateTextReport = true;
            public String textReportFilename = "comparison_report.txt";
        }

        public static class ComparisonMetrics {
            public FieldChanges fieldChanges;
            public LineChanges lineChanges;
            public BurstMetrics burstMetrics;
            public TimingAnalysis timingAnalysis;
            public SequenceAnalysis sequenceAnalysis;
            public StatisticalAnalysis statisticalAnalysis;
        }

        public static class FieldChanges {
            public boolean enabled = true;
            public String[] fields;
        }

        public static class LineChanges {
            public boolean enabled = true;
            public String[] metrics;
        }

        public static class BurstMetrics {
            public boolean enabled = true;
            public String[] metrics;
            public BurstThreshold burstThreshold;

            public static class BurstThreshold {
                public int minMessages = 3;
                public int maxIntervalMs = 100;
            }
        }

        public static class TimingAnalysis {
            public boolean enabled = true;
            public String[] metrics;
        }

        public static class SequenceAnalysis {
            public boolean enabled = true;
            public String[] metrics;
        }

        public static class StatisticalAnalysis {
            public boolean enabled = true;
            public String[] metrics;
        }

        public static class ComparisonSettings {
            public boolean alignByTimestamp = true;
            public int toleranceMs = 10;
            public int includeContextLines = 5;
            public boolean highlightAnomalies = true;
        }
    }

    public static class ComparisonReport {
        public String benignDataPath;
        public String attackDatasetPath;
        public Date analysisDate;
        public int benignMessageCount;
        public int attackMessageCount;
        public FieldChangeReport fieldChanges;
        public LineChangeReport lineChanges;
        public BurstReport burstMetrics;
        public TimingReport timingAnalysis;
        public SequenceReport sequenceAnalysis;
        public StatisticalReport statisticalAnalysis;

        public static class FieldChangeReport {
            public Map<String, FieldStats> fieldStats = new HashMap<>();

            public static class FieldStats {
                public int totalChanges;
                public int uniqueValues;
                public double changePercentage;
                public List<String> exampleChanges = new ArrayList<>();
            }
        }

        public static class LineChangeReport {
            public int totalLinesChanged;
            public int linesAdded;
            public int linesRemoved;
            public double changePercentage;
        }

        public static class BurstReport {
            public int burstCount;
            public double burstDurationAvg;
            public double burstDurationMax;
            public double burstIntensity;
            public double interBurstGapAvg;
            public List<BurstInstance> bursts = new ArrayList<>();

            public static class BurstInstance {
                public int startIndex;
                public int messageCount;
                public double durationMs;
                public double avgGapMs;
            }
        }

        public static class TimingReport {
            public double averageInterMessageGap;
            public double timingVariance;
            public int anomalousGapsCount;
            public Map<String, Integer> timingDistribution = new HashMap<>();
        }

        public static class SequenceReport {
            public int sequenceBreaks;
            public int stnumJumps;
            public int sqnumJumps;
            public int outOfOrderMessages;
            public List<String> anomalyDetails = new ArrayList<>();
        }

        public static class StatisticalReport {
            public Map<String, Double> meanComparison = new HashMap<>();
            public Map<String, Double> varianceComparison = new HashMap<>();
            public double distributionSimilarity;
            public double correlationCoefficient;
        }
    }

    public static void execute(String configPath) throws IOException {
        LOGGER.info("=== Starting Compare Action ===");

        // Load and parse config
        Gson gson = new Gson();
        Config config;
        try (java.io.FileReader reader = new java.io.FileReader(configPath)) {
            config = gson.fromJson(reader, Config.class);
        }

        // Verify input files
        if (config.input.verifyFiles) {
            if (!ActionConfigLoader.verifyFile(config.input.benignDataPath, "Benign data")) {
                throw new IOException("Benign data file verification failed");
            }
            if (!ActionConfigLoader.verifyFile(config.input.attackDatasetPath, "Attack dataset")) {
                throw new IOException("Attack dataset file verification failed");
            }
        }

        // Load datasets
        LOGGER.info("Loading benign data from: " + config.input.benignDataPath);
        List<Goose> benignMessages = loadMessages(config.input.benignDataPath);
        LOGGER.info("Loaded " + benignMessages.size() + " benign messages");

        LOGGER.info("Loading attack dataset from: " + config.input.attackDatasetPath);
        List<Goose> attackMessages = loadMessages(config.input.attackDatasetPath);
        LOGGER.info("Loaded " + attackMessages.size() + " attack messages");

        // Perform comparison
        ComparisonReport report = new ComparisonReport();
        report.benignDataPath = config.input.benignDataPath;
        report.attackDatasetPath = config.input.attackDatasetPath;
        report.analysisDate = new Date();
        report.benignMessageCount = benignMessages.size();
        report.attackMessageCount = attackMessages.size();

        // Field changes analysis
        if (config.comparisonMetrics.fieldChanges != null && config.comparisonMetrics.fieldChanges.enabled) {
            LOGGER.info("Analyzing field changes...");
            report.fieldChanges = analyzeFieldChanges(benignMessages, attackMessages, config);
        }

        // Line changes analysis
        if (config.comparisonMetrics.lineChanges != null && config.comparisonMetrics.lineChanges.enabled) {
            LOGGER.info("Analyzing line changes...");
            report.lineChanges = analyzeLineChanges(benignMessages, attackMessages);
        }

        // Burst metrics analysis
        if (config.comparisonMetrics.burstMetrics != null && config.comparisonMetrics.burstMetrics.enabled) {
            LOGGER.info("Analyzing burst patterns...");
            report.burstMetrics = analyzeBurstMetrics(attackMessages, config);
        }

        // Timing analysis
        if (config.comparisonMetrics.timingAnalysis != null && config.comparisonMetrics.timingAnalysis.enabled) {
            LOGGER.info("Analyzing timing patterns...");
            report.timingAnalysis = analyzeTimingPatterns(benignMessages, attackMessages);
        }

        // Sequence analysis
        if (config.comparisonMetrics.sequenceAnalysis != null && config.comparisonMetrics.sequenceAnalysis.enabled) {
            LOGGER.info("Analyzing sequence violations...");
            report.sequenceAnalysis = analyzeSequenceViolations(attackMessages);
        }

        // Statistical analysis
        if (config.comparisonMetrics.statisticalAnalysis != null && config.comparisonMetrics.statisticalAnalysis.enabled) {
            LOGGER.info("Performing statistical analysis...");
            report.statisticalAnalysis = performStatisticalAnalysis(benignMessages, attackMessages);
        }

        // Save report
        saveReport(report, config);

        LOGGER.info("=== Compare Action Completed Successfully ===");
    }

    private static List<Goose> loadMessages(String filepath) throws IOException {
        List<Goose> messages = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            boolean inData = false;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("%") || line.startsWith("#")) continue;

                if (line.toLowerCase().startsWith("@data")) {
                    inData = true;
                    continue;
                }

                if (inData || filepath.toLowerCase().endsWith(".csv")) {
                    try {
                        String[] parts = line.split(",");
                        if (parts.length >= 9) {
                            int cbStatus = Integer.parseInt(parts[4].trim());
                            int stNum = Integer.parseInt(parts[5].trim());
                            int sqNum = Integer.parseInt(parts[6].trim());
                            double timestamp = Double.parseDouble(parts[7].trim());
                            double t = Double.parseDouble(parts[8].trim());
                            String label = parts.length > 9 ? parts[parts.length - 1].trim() : "unknown";
                            messages.add(new Goose(cbStatus, stNum, sqNum, timestamp, t, label));
                        }
                    } catch (Exception e) {
                        // Skip malformed lines
                    }
                }
            }
        }
        return messages;
    }

    private static ComparisonReport.FieldChangeReport analyzeFieldChanges(
            List<Goose> benign, List<Goose> attack, Config config) {
        ComparisonReport.FieldChangeReport report = new ComparisonReport.FieldChangeReport();

        for (String field : config.comparisonMetrics.fieldChanges.fields) {
            ComparisonReport.FieldChangeReport.FieldStats stats = 
                new ComparisonReport.FieldChangeReport.FieldStats();
            
            Set<Object> uniqueValues = new HashSet<>();
            int changes = 0;

            for (int i = 0; i < Math.min(benign.size(), attack.size()); i++) {
                Object benignVal = getFieldValue(benign.get(i), field);
                Object attackVal = getFieldValue(attack.get(i), field);
                
                if (!Objects.equals(benignVal, attackVal)) {
                    changes++;
                    if (stats.exampleChanges.size() < 5) {
                        stats.exampleChanges.add(String.format("%s: %s → %s", field, benignVal, attackVal));
                    }
                }
                uniqueValues.add(attackVal);
            }

            stats.totalChanges = changes;
            stats.uniqueValues = uniqueValues.size();
            stats.changePercentage = (changes * 100.0) / Math.min(benign.size(), attack.size());
            report.fieldStats.put(field, stats);
        }

        return report;
    }

    private static Object getFieldValue(Goose message, String field) {
        switch (field.toLowerCase()) {
            case "stnum": return message.getStNum();
            case "sqnum": return message.getSqNum();
            case "timestamp": return message.getTimestamp();
            case "cbstatus": return message.getCbStatus();
            case "t": return message.getT();
            default: return null;
        }
    }

    private static ComparisonReport.LineChangeReport analyzeLineChanges(
            List<Goose> benign, List<Goose> attack) {
        ComparisonReport.LineChangeReport report = new ComparisonReport.LineChangeReport();
        
        int totalChanges = 0;
        for (int i = 0; i < Math.min(benign.size(), attack.size()); i++) {
            if (!messagesEqual(benign.get(i), attack.get(i))) {
                totalChanges++;
            }
        }

        report.totalLinesChanged = totalChanges;
        report.linesAdded = Math.max(0, attack.size() - benign.size());
        report.linesRemoved = Math.max(0, benign.size() - attack.size());
        report.changePercentage = (totalChanges * 100.0) / benign.size();

        return report;
    }

    private static boolean messagesEqual(Goose m1, Goose m2) {
        return m1.getStNum() == m2.getStNum() &&
               m1.getSqNum() == m2.getSqNum() &&
               m1.getCbStatus() == m2.getCbStatus() &&
               Math.abs(m1.getTimestamp() - m2.getTimestamp()) < 0.001;
    }

    private static ComparisonReport.BurstReport analyzeBurstMetrics(
            List<Goose> messages, Config config) {
        ComparisonReport.BurstReport report = new ComparisonReport.BurstReport();
        
        int minMessages = config.comparisonMetrics.burstMetrics.burstThreshold.minMessages;
        double maxInterval = config.comparisonMetrics.burstMetrics.burstThreshold.maxIntervalMs / 1000.0;

        List<ComparisonReport.BurstReport.BurstInstance> bursts = new ArrayList<>();
        int burstStart = 0;
        int burstCount = 1;

        for (int i = 1; i < messages.size(); i++) {
            double gap = (messages.get(i).getTimestamp() - messages.get(i - 1).getTimestamp()) * 1000;
            
            if (gap <= maxInterval) {
                burstCount++;
            } else {
                if (burstCount >= minMessages) {
                    ComparisonReport.BurstReport.BurstInstance burst = 
                        new ComparisonReport.BurstReport.BurstInstance();
                    burst.startIndex = burstStart;
                    burst.messageCount = burstCount;
                    burst.durationMs = (messages.get(i - 1).getTimestamp() - 
                                       messages.get(burstStart).getTimestamp()) * 1000;
                    burst.avgGapMs = burst.durationMs / burstCount;
                    bursts.add(burst);
                }
                burstStart = i;
                burstCount = 1;
            }
        }

        report.bursts = bursts;
        report.burstCount = bursts.size();
        
        if (!bursts.isEmpty()) {
            report.burstDurationAvg = bursts.stream()
                .mapToDouble(b -> b.durationMs).average().orElse(0);
            report.burstDurationMax = bursts.stream()
                .mapToDouble(b -> b.durationMs).max().orElse(0);
            report.burstIntensity = bursts.stream()
                .mapToInt(b -> b.messageCount).sum() / (double) messages.size();
        }

        return report;
    }

    private static ComparisonReport.TimingReport analyzeTimingPatterns(
            List<Goose> benign, List<Goose> attack) {
        ComparisonReport.TimingReport report = new ComparisonReport.TimingReport();

        double sumGaps = 0;
        double sumSqGaps = 0;
        int anomalousCount = 0;

        for (int i = 1; i < attack.size(); i++) {
            double gap = (attack.get(i).getTimestamp() - attack.get(i - 1).getTimestamp()) * 1000;
            sumGaps += gap;
            sumSqGaps += gap * gap;
            
            if (gap < 1 || gap > 2000) anomalousCount++;
        }

        report.averageInterMessageGap = sumGaps / (attack.size() - 1);
        double mean = report.averageInterMessageGap;
        report.timingVariance = (sumSqGaps / (attack.size() - 1)) - (mean * mean);
        report.anomalousGapsCount = anomalousCount;

        return report;
    }

    private static ComparisonReport.SequenceReport analyzeSequenceViolations(List<Goose> messages) {
        ComparisonReport.SequenceReport report = new ComparisonReport.SequenceReport();

        int prevStNum = messages.get(0).getStNum();
        int prevSqNum = messages.get(0).getSqNum();

        for (int i = 1; i < messages.size(); i++) {
            Goose msg = messages.get(i);
            
            if (msg.getStNum() != prevStNum && msg.getStNum() != prevStNum + 1) {
                report.stnumJumps++;
                report.anomalyDetails.add(String.format("Line %d: StNum jump from %d to %d", 
                    i, prevStNum, msg.getStNum()));
            }
            
            if (msg.getStNum() == prevStNum && msg.getSqNum() != prevSqNum + 1) {
                report.sqnumJumps++;
                report.anomalyDetails.add(String.format("Line %d: SqNum jump from %d to %d", 
                    i, prevSqNum, msg.getSqNum()));
            }

            prevStNum = msg.getStNum();
            prevSqNum = msg.getSqNum();
        }

        report.sequenceBreaks = report.stnumJumps + report.sqnumJumps;

        return report;
    }

    private static ComparisonReport.StatisticalReport performStatisticalAnalysis(
            List<Goose> benign, List<Goose> attack) {
        ComparisonReport.StatisticalReport report = new ComparisonReport.StatisticalReport();

        // Calculate means for timestamps
        double benignTimeMean = benign.stream().mapToDouble(Goose::getTimestamp).average().orElse(0);
        double attackTimeMean = attack.stream().mapToDouble(Goose::getTimestamp).average().orElse(0);
        report.meanComparison.put("timestamp", Math.abs(attackTimeMean - benignTimeMean));

        // Calculate variance
        double benignTimeVar = benign.stream()
            .mapToDouble(m -> Math.pow(m.getTimestamp() - benignTimeMean, 2))
            .average().orElse(0);
        double attackTimeVar = attack.stream()
            .mapToDouble(m -> Math.pow(m.getTimestamp() - attackTimeMean, 2))
            .average().orElse(0);
        report.varianceComparison.put("timestamp", Math.abs(attackTimeVar - benignTimeVar));

        return report;
    }

    private static void saveReport(ComparisonReport report, Config config) throws IOException {
        File outDir = new File(config.output.directory);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        // Save JSON report
        String jsonPath = config.output.directory + File.separator + config.output.filename;
        try (FileWriter writer = new FileWriter(jsonPath)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(report, writer);
        }
        LOGGER.info("JSON report saved to: " + jsonPath);

        // Save text report if requested
        if (config.output.generateTextReport) {
            String textPath = config.output.directory + File.separator + config.output.textReportFilename;
            saveTextReport(report, textPath);
            LOGGER.info("Text report saved to: " + textPath);
        }
    }

    private static void saveTextReport(ComparisonReport report, String filepath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filepath))) {
            writer.println("=".repeat(80));
            writer.println("ERENO COMPARISON REPORT");
            writer.println("=".repeat(80));
            writer.println();
            writer.println("Analysis Date: " + report.analysisDate);
            writer.println("Benign Data: " + report.benignDataPath);
            writer.println("Attack Dataset: " + report.attackDatasetPath);
            writer.println("Benign Messages: " + report.benignMessageCount);
            writer.println("Attack Messages: " + report.attackMessageCount);
            writer.println();

            if (report.fieldChanges != null) {
                writer.println("-".repeat(80));
                writer.println("FIELD CHANGES ANALYSIS");
                writer.println("-".repeat(80));
                for (Map.Entry<String, ComparisonReport.FieldChangeReport.FieldStats> entry : 
                     report.fieldChanges.fieldStats.entrySet()) {
                    writer.printf("Field: %s\n", entry.getKey());
                    writer.printf("  Total Changes: %d (%.2f%%)\n", 
                        entry.getValue().totalChanges, entry.getValue().changePercentage);
                    writer.printf("  Unique Values: %d\n", entry.getValue().uniqueValues);
                    if (!entry.getValue().exampleChanges.isEmpty()) {
                        writer.println("  Examples:");
                        for (String example : entry.getValue().exampleChanges) {
                            writer.println("    - " + example);
                        }
                    }
                    writer.println();
                }
            }

            if (report.lineChanges != null) {
                writer.println("-".repeat(80));
                writer.println("LINE CHANGES ANALYSIS");
                writer.println("-".repeat(80));
                writer.printf("Total Lines Changed: %d (%.2f%%)\n", 
                    report.lineChanges.totalLinesChanged, report.lineChanges.changePercentage);
                writer.printf("Lines Added: %d\n", report.lineChanges.linesAdded);
                writer.printf("Lines Removed: %d\n", report.lineChanges.linesRemoved);
                writer.println();
            }

            if (report.burstMetrics != null) {
                writer.println("-".repeat(80));
                writer.println("BURST METRICS ANALYSIS");
                writer.println("-".repeat(80));
                writer.printf("Burst Count: %d\n", report.burstMetrics.burstCount);
                writer.printf("Average Burst Duration: %.2f ms\n", report.burstMetrics.burstDurationAvg);
                writer.printf("Maximum Burst Duration: %.2f ms\n", report.burstMetrics.burstDurationMax);
                writer.printf("Burst Intensity: %.4f\n", report.burstMetrics.burstIntensity);
                writer.println();
            }

            if (report.timingAnalysis != null) {
                writer.println("-".repeat(80));
                writer.println("TIMING ANALYSIS");
                writer.println("-".repeat(80));
                writer.printf("Average Inter-Message Gap: %.2f ms\n", 
                    report.timingAnalysis.averageInterMessageGap);
                writer.printf("Timing Variance: %.2f\n", report.timingAnalysis.timingVariance);
                writer.printf("Anomalous Gaps: %d\n", report.timingAnalysis.anomalousGapsCount);
                writer.println();
            }

            if (report.sequenceAnalysis != null) {
                writer.println("-".repeat(80));
                writer.println("SEQUENCE VIOLATION ANALYSIS");
                writer.println("-".repeat(80));
                writer.printf("Total Sequence Breaks: %d\n", report.sequenceAnalysis.sequenceBreaks);
                writer.printf("StNum Jumps: %d\n", report.sequenceAnalysis.stnumJumps);
                writer.printf("SqNum Jumps: %d\n", report.sequenceAnalysis.sqnumJumps);
                writer.printf("Out-of-Order Messages: %d\n", report.sequenceAnalysis.outOfOrderMessages);
                if (!report.sequenceAnalysis.anomalyDetails.isEmpty()) {
                    writer.println("\nAnomaly Details (first 10):");
                    for (int i = 0; i < Math.min(10, report.sequenceAnalysis.anomalyDetails.size()); i++) {
                        writer.println("  - " + report.sequenceAnalysis.anomalyDetails.get(i));
                    }
                }
                writer.println();
            }

            writer.println("=".repeat(80));
            writer.println("END OF REPORT");
            writer.println("=".repeat(80));
        }
    }
}
