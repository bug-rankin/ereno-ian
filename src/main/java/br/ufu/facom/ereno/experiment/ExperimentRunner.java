package br.ufu.facom.ereno.experiment;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import br.ufu.facom.ereno.SingleSource;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.evaluation.support.GenericEvaluation;
import br.ufu.facom.ereno.evaluation.support.GenericResultado;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;

/**
 * Small experiment runner used by optimization loops.
 *
 * Usage:
 * java -cp target/ERENO-1.0-SNAPSHOT-shaded.jar br.ufu.facom.ereno.experiment.ExperimentRunner
 *      <configPath> <outputDir> [classifier=j48] [attack=random_replay] [seed] [trainFraction=0.7]
 *
 * It will:
 *  - load the provided JSON config via ConfigLoader.load(path)
 *  - generate a dataset (ARFF) using SingleSource.lightweightDataset into <outputDir>/generated.arff
 *  - split into train/test (trainFraction)
 *  - train and evaluate the chosen classifier and print a small JSON with metrics to stdout
 */
public class ExperimentRunner {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: ExperimentRunner <configPath> <outputDir> [classifier=j48] [attack=random_replay] [seed] [trainFraction=0.7]");
            System.exit(1);
        }

        // Force headless mode to prevent any AWT/Swing dialogs (Weka GUI components)
        // from popping up during automated runs or optimization loops. Some Weka
        // editors attempt to open GUI dialogs which blocks optimization runs on
        // CI or when running as a subprocess; setting this property avoids that.
        System.setProperty("java.awt.headless", "true");

        String configPath = args[0];
        String outputDir = args[1];
        String classifier = args.length > 2 ? args[2] : "j48";
        String attack = args.length > 3 ? args[3] : "random_replay";
        long seed = args.length > 4 ? Long.parseLong(args[4]) : System.nanoTime();
        double trainFraction = args.length > 5 ? Double.parseDouble(args[5]) : 0.7;

        try {
            // 1) Load config from provided path (does not mutate repository file)
            ConfigLoader.load(configPath);

            // Ensure output dir exists
            File out = new File(outputDir);
            if (!out.exists()) out.mkdirs();

            // 2) Generate dataset
            String generated = new File(out, "generated.arff").getAbsolutePath();
            SingleSource.lightweightDataset(generated, false, "arff");

            // Check for a baseline train file passed as a system property. If present,
            // use that file as the training set and use the newly-generated ARFF as the
            // test set (no internal split). This allows external optimizers to use the
            // first generated dataset as the canonical training data and every subsequent
            // generated dataset as a separate test set.
            String baselineTrainFile = System.getProperty("ereno.trainFile");

            // 3) Load dataset and split
            Instances all;
            try {
                // Validate and (if needed) fix the generated ARFF so any nominal @attribute {...}
                // includes all tokens that appear in the @data section. Weka will throw
                // an IOException when a data row contains a nominal value that wasn't declared
                // in the header (we've observed this for MAC-like tokens in ethSrc/ethDst).
                try {
                    ensureArffNominalTokens(generated);
                    // If a baseline train file is provided, also ensure its nominal tokens are present
                    if (baselineTrainFile != null && !baselineTrainFile.isEmpty()) {
                        try { ensureArffNominalTokens(baselineTrainFile); } catch (IOException ex) { /* ignore */ }
                    }
                } catch (IOException ex) {
                    System.err.println("Warning: failed to validate/fix ARFF nominal tokens: " + ex.getMessage());
                }
                DataSource src = new DataSource(generated);
                all = src.getDataSet();
            } catch (Exception ioe) {
                // Fail fast: print the generated ARFF path and a snippet to help debugging
                System.err.println("Failed to load generated ARFF: " + generated);
                System.err.println("Exception: " + ioe.getClass().getName() + ": " + ioe.getMessage());
                // print first ~200 lines of the ARFF for debugging
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(generated))) {
                    String line;
                    int i = 0;
                    while ((line = br.readLine()) != null && i < 200) {
                        System.err.println(line);
                        i++;
                    }
                } catch (Exception ex) {
                    System.err.println("Also failed to read generated file for debugging: " + ex.getClass().getName() + ": " + ex.getMessage());
                }
                // exit with a distinct exit code so external orchestrators can detect this as a generator/format error
                System.err.println("Exiting due to ARFF load failure.");
                System.exit(3);
                return; // unreachable, but keeps compiler happy
            }
            if (baselineTrainFile != null && !baselineTrainFile.isEmpty()) {
                // Use baseline train file and the newly-generated ARFF as test
                DataSource trainSrc = new DataSource(baselineTrainFile);
                Instances train = trainSrc.getDataSet();
                if (train.classIndex() == -1) train.setClassIndex(train.numAttributes() - 1);

                DataSource testSrc = new DataSource(generated);
                Instances test = testSrc.getDataSet();
                if (test.classIndex() == -1) test.setClassIndex(test.numAttributes() - 1);

                // Save train/test
                String trainPath = new File(out, "train.arff").getAbsolutePath();
                String testPath = new File(out, "test.arff").getAbsolutePath();
                saveArff(train, trainPath);
                saveArff(test, testPath);

                // 4) Evaluate using project's GenericEvaluation (DatasetEval style) to get F1 score
                GenericResultado result;
                if ("j48".equalsIgnoreCase(classifier)) {
                    result = GenericEvaluation.runSingleClassifierJ48(train, test);
                } else {
                    result = GenericEvaluation.runSingleClassifier(train, test);
                }

                double f1 = result.getF1Score();
                double accuracy = result.getAcuracia();

                String outJson = String.format(
                    "{\"train\":\"%s\",\"test\":\"%s\",\"classifier\":\"%s\",\"attack\":\"%s\",\"seed\":%d,\"metric_f1\":%.6f,\"accuracy\":%.6f,\"VP\":%d,\"VN\":%d,\"FP\":%d,\"FN\":%d}",
                    trainPath, testPath, classifier, attack, seed, f1, accuracy,
                    (int) result.getVP(), (int) result.getVN(), (int) result.getFP(), (int) result.getFN());
                System.out.println(outJson);

                return;
            }

            if (all.classIndex() == -1) all.setClassIndex(all.numAttributes() - 1);

            // randomize and split
            all.randomize(new Random(seed));
            int trainSize = (int) Math.round(all.numInstances() * trainFraction);
            if (trainSize < 1) trainSize = 1;
            if (trainSize >= all.numInstances()) trainSize = all.numInstances() - 1;

            Instances train = new Instances(all, 0, trainSize);
            Instances test = new Instances(all, trainSize, all.numInstances() - trainSize);

            // Save train/test
            String trainPath = new File(out, "train.arff").getAbsolutePath();
            String testPath = new File(out, "test.arff").getAbsolutePath();
            saveArff(train, trainPath);
            saveArff(test, testPath);

            // 4) Evaluate using project's GenericEvaluation (DatasetEval style) to get F1 score
            GenericResultado result;
            if ("j48".equalsIgnoreCase(classifier)) {
                result = GenericEvaluation.runSingleClassifierJ48(train, test);
            } else {
                result = GenericEvaluation.runSingleClassifier(train, test);
            }

        double f1 = result.getF1Score();
        double accuracy = result.getAcuracia();

        // 5) Print results as JSON to stdout (include metric_f1 for optimizer)
        // also include confusion counts so the optimizer can distinguish perfect scores
        String outJson = String.format(
            "{\"train\":\"%s\",\"test\":\"%s\",\"classifier\":\"%s\",\"attack\":\"%s\",\"seed\":%d,\"metric_f1\":%.6f,\"accuracy\":%.6f,\"VP\":%d,\"VN\":%d,\"FP\":%d,\"FN\":%d}",
            trainPath, testPath, classifier, attack, seed, f1, accuracy,
            (int) result.getVP(), (int) result.getVN(), (int) result.getFP(), (int) result.getFN());
        System.out.println(outJson);

        } catch (Exception e) {
            System.err.println("Error during experiment execution: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Ensure that the ARFF at 'path' has any @attribute NAME {..} headers contain all
     * nominal tokens that appear in the corresponding column of the @data section. If any tokens are
     * missing, the file is rewritten with updated attribute headers containing the union.
     */
    private static void ensureArffNominalTokens(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) return;

        java.util.List<String> lines = java.nio.file.Files.readAllLines(f.toPath());

        int dataIndex = -1;
        // collect attribute lines (index in file -> attribute column position)
        java.util.List<Integer> attrLineIndices = new java.util.ArrayList<>();
        java.util.List<String> attrLines = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.toLowerCase().startsWith("@data")) { dataIndex = i; break; }
            if (l.toLowerCase().startsWith("@attribute")) {
                attrLineIndices.add(i);
                attrLines.add(lines.get(i));
            }
        }

        if (dataIndex == -1 || attrLineIndices.isEmpty()) return; // nothing to do

        // determine which attributes are nominal (contain {..}) and record their declared tokens
        java.util.Map<Integer, java.util.Set<String>> declaredPerCol = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, Integer> attrLineToCol = new java.util.HashMap<>();
        int col = 0;
        for (int idx = 0; idx < attrLines.size(); idx++) {
            String attr = attrLines.get(idx);
            int fileLine = attrLineIndices.get(idx);
            int open = attr.indexOf('{');
            int close = attr.indexOf('}', open);
            attrLineToCol.put(fileLine, col);
            if (open >= 0 && close > open) {
                String inside = attr.substring(open + 1, close);
                java.util.Set<String> declared = new java.util.LinkedHashSet<>();
                for (String tok : inside.split(",")) {
                    String t = tok.trim();
                    if (!t.isEmpty()) declared.add(t);
                }
                declaredPerCol.put(col, declared);
            }
            col++;
        }

        if (declaredPerCol.isEmpty()) return; // no nominal attributes to check

        // collect missing tokens per column from the @data section
        java.util.Map<Integer, java.util.Set<String>> missingPerCol = new java.util.HashMap<>();
        for (int i = dataIndex + 1; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.isEmpty() || l.startsWith("%")) continue;
            String[] parts = l.split(",");
            for (java.util.Map.Entry<Integer, java.util.Set<String>> e : declaredPerCol.entrySet()) {
                int colIdx = e.getKey();
                if (colIdx >= parts.length) continue;
                String val = parts[colIdx].trim();
                if (val.isEmpty()) continue;
                java.util.Set<String> declared = e.getValue();
                if (!declared.contains(val)) {
                    missingPerCol.computeIfAbsent(colIdx, k -> new java.util.LinkedHashSet<>()).add(val);
                }
            }
        }

        if (missingPerCol.isEmpty()) return; // nothing to patch

        // For each attribute line that corresponds to a nominal column, append missing tokens preserving order
        // Build a map from column index -> file line index
        java.util.Map<Integer, Integer> colToAttrLine = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, Integer> e : attrLineToCol.entrySet()) {
            colToAttrLine.put(e.getValue(), e.getKey());
        }

        for (java.util.Map.Entry<Integer, java.util.Set<String>> e : missingPerCol.entrySet()) {
            int colIdx = e.getKey();
            java.util.Set<String> missing = e.getValue();
            Integer fileLineIdx = colToAttrLine.get(colIdx);
            if (fileLineIdx == null) continue;
            String orig = lines.get(fileLineIdx);
            int open = orig.indexOf('{');
            int close = orig.indexOf('}', open);
            if (open < 0 || close < 0) continue;
            String inside = orig.substring(open + 1, close);
            java.util.List<String> declared = new java.util.ArrayList<>();
            for (String tok : inside.split(",")) {
                String t = tok.trim(); if (!t.isEmpty()) declared.add(t);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(orig.substring(0, open + 1));
            boolean first = true;
            for (String tok : declared) {
                if (!first) sb.append(", "); sb.append(tok); first = false;
            }
            for (String tok : missing) {
                if (!first) sb.append(", "); sb.append(tok); first = false;
            }
            sb.append(orig.substring(close));
            lines.set(fileLineIdx, sb.toString());
            System.err.println("Patched ARFF attribute line (col " + colIdx + ") to include missing tokens: " + missing);
        }

        // write updated file
        File tmp = new File(path + ".tmp");
        java.nio.file.Files.write(tmp.toPath(), lines);
        java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void saveArff(Instances data, String path) throws IOException {
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(path));
        saver.writeBatch();
    }
}
