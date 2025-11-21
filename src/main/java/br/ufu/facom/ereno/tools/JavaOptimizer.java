package br.ufu.facom.ereno.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Java optimizer with a simple hill-climb stage.
 * - trial 0: baseline (no parameter changes)
 * - randomInit trials: random sampling
 * - remaining trials: mutate current best (greedy hill-climb)
 *
 * Uses a fixed dataset randomSeed so only attack parameters change.
 */
public class JavaOptimizer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RNG = new Random();
    private static final Path BASE_CFG = Path.of("config", "configparams.json");
    private static final Path JAR = Path.of("target", "ERENO-1.0-SNAPSHOT-uber.jar");

    public static void main(String[] args) throws Exception {
        int trials = 50;
        String attackKey = null;
        long baseSeed = 123;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--trials": trials = Integer.parseInt(args[++i]); break;
                case "--attack": attackKey = args[++i]; break;
                case "--seed": baseSeed = Long.parseLong(args[++i]); break;
                default: break;
            }
        }

        if (attackKey == null) {
            System.err.println("Usage: JavaOptimizer --attack <attackKey> [--trials N] [--seed S]");
            System.exit(1);
        }

        System.out.println("JavaOptimizer: attackKey=" + attackKey + " trials=" + trials + " seed=" + baseSeed);

        JsonObject base = loadJson(BASE_CFG.toFile());
        JsonObject attacksParams = base.getAsJsonObject("attacksParams");
        if (attacksParams == null || !attacksParams.has(attackKey)) {
            System.err.println("Attack key not found in config: " + attackKey);
            System.exit(1);
        }

        JsonElement attackCfg = attacksParams.get(attackKey);

    double bestVal = Double.POSITIVE_INFINITY;
    JsonObject bestPatch = null;
    String baselineTrainPath = null;

        // Use a fixed randomSeed for all trials so that only attack parameters change
        long childSeed = baseSeed;

        int randomInit = Math.min(10, Math.max(1, trials / 5));
        if (trials <= 5) randomInit = Math.max(1, trials - 1);

        for (int t = 0; t < trials; t++) {
            JsonObject patch = new JsonObject();
            JsonObject attacksRoot = new JsonObject();
            JsonObject attackPatch = new JsonObject();

            // For trial 0 use the unmodified base attack config as the initial baseline
            if (t > 0) {
                // small helper: traverse and fill attackPatch with randomized values
                traverseAndSample(attackKey, new ArrayList<>(), attackCfg, attackPatch);
            }

            attacksRoot.add(attackKey, attackPatch);
            patch.add("attacksParams", attacksRoot);
            // reduce dataset size for faster runs
            JsonObject gooseFlow = new JsonObject();
            gooseFlow.addProperty("numberOfMessages", 2000);
            patch.add("gooseFlow", gooseFlow);
            // keep randomSeed constant across trials to avoid confounding the search
            patch.addProperty("randomSeed", childSeed);

            // write patch to temp dir
            Path tmp = Files.createTempDirectory("javaopt_run_");
            Path cfgFile = tmp.resolve("cfg.json");
            // merge base + patch and write full config
            JsonObject merged = deepMerge(base.deepCopy(), patch);
            Files.writeString(cfgFile, GSON.toJson(merged));
            Path outdir = tmp.resolve("out");
            Files.createDirectories(outdir);

            // spawn the shaded jar as subprocess to avoid System.exit collisions
            List<String> cmd = new ArrayList<>();
            cmd.add("java"); cmd.add("-Djava.awt.headless=true");
            // if we have already produced a baseline train file, pass it to the child JVM
            if (baselineTrainPath != null) cmd.add("-Dereno.trainFile=" + baselineTrainPath);
            cmd.add("-cp"); cmd.add(JAR.toString());
            cmd.add("br.ufu.facom.ereno.experiment.ExperimentRunner");
            cmd.add(cfgFile.toString());
            cmd.add(outdir.toString());
            cmd.add("j48");
            cmd.add(attackKey);
            cmd.add(Long.toString(childSeed));
            // third param (optional) is a score threshold used by older drivers; keep for compatibility
            cmd.add("0.7");

            System.out.println("CMD: " + String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            int rc = p.waitFor();

            // after trial 0, capture the generated train.arff to use as baseline for later trials
            if (t == 0 && rc == 0) {
                Path baselineTrain = outdir.resolve("train.arff");
                if (Files.exists(baselineTrain)) {
                    baselineTrainPath = baselineTrain.toAbsolutePath().toString();
                    System.out.println("Saved baseline train file: " + baselineTrainPath);
                }
            }

            String stdout = out.toString();
            String json = extractFirstJson(stdout);
            double metric = 2.0; // penalty default
            if (json != null) {
                try {
                    // ExperimentRunner prints unescaped Windows backslashes in paths which makes the
                    // JSON invalid for strict parsers. Sanitize by escaping backslashes before parsing.
                    String sanitized = json.replace("\\", "\\\\");
                    JsonObject res = GSON.fromJson(sanitized, JsonObject.class);
                    metric = res.has("metric_f1") ? res.get("metric_f1").getAsDouble() : 1.0;
                } catch (Exception ex) {
                    // fallback: try to extract metric_f1 with a regex from the raw json snippet
                    try {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"metric_f1\\\"\\s*:\\s*([0-9.]+)").matcher(json);
                        if (m.find()) {
                            metric = Double.parseDouble(m.group(1));
                        } else {
                            System.err.println("Failed to parse JSON from runner stdout: " + ex.getMessage());
                        }
                    } catch (Exception e2) {
                        System.err.println("Failed to fallback-parse metric_f1: " + e2.getMessage());
                    }
                }
            } else {
                System.err.println("No JSON result from runner (rc=" + rc + "). stdout:\n" + stdout);
            }

            System.out.println(String.format("Trial %d metric_f1=%.6f rc=%d", t, metric, rc));

            if (metric < bestVal) {
                bestVal = metric;
                bestPatch = patch.deepCopy();
                // copy outdir contents into target for inspection
                Path targetBest = Path.of("target", "javaopt_best_run");
                if (Files.exists(targetBest)) deleteRecursively(targetBest.toFile());
                Files.createDirectories(targetBest);
                // copy generated train/test if present
                try {
                    Files.walk(outdir).forEach(pth -> {
                        try {
                            Path rel = outdir.relativize(pth);
                            Path dst = targetBest.resolve(rel.toString());
                            if (Files.isDirectory(pth)) {
                                if (!Files.exists(dst)) Files.createDirectories(dst);
                            } else {
                                Files.copy(pth, dst, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) { /* ignore */ }
                    });
                } catch (IOException e) { /* ignore */ }
            }

            // keep the tmp dir for inspection (do not delete)
        }

        System.out.println("Best metric_f1=" + bestVal);
        if (bestPatch != null) {
            Path outBest = Path.of("target", "opt_best_config.json");
            // write best_patch merged into base config
            JsonObject merged = deepMerge(loadJson(BASE_CFG.toFile()).deepCopy(), bestPatch);
            Files.createDirectories(outBest.getParent());
            Files.writeString(outBest, GSON.toJson(merged));
            System.out.println("Wrote best config to " + outBest.toString());
        }
    }

    private static void traverseAndSample(String attackKey, List<String> prefix, JsonElement node, JsonObject out) {
        if (node == null || node.isJsonNull()) return;
        if (node.isJsonObject()) {
            JsonObject obj = node.getAsJsonObject();
            if (obj.has("min") && obj.has("max") && obj.get("min").isJsonPrimitive() && obj.get("max").isJsonPrimitive()) {
                JsonPrimitive pmin = obj.getAsJsonPrimitive("min");
                JsonPrimitive pmax = obj.getAsJsonPrimitive("max");
                if (pmin.isNumber() && pmax.isNumber()) {
                    // sample between min and max
                    if (isIntegral(pmin) && isIntegral(pmax)) {
                        int lo = pmin.getAsInt();
                        int hi = pmax.getAsInt();
                        if (hi <= lo) hi = lo + 1;
                        int span = Math.max(1, hi - lo);
                        int baseVal = randInt(lo, hi);
                        // increase sampling window to ~30% of span to be more exploratory
                        int maxWidth = Math.max(1, (int) Math.max(1, Math.round(span * 0.30)));
                        int width = randInt(0, maxWidth);
                        int vmin = Math.max(lo, baseVal - width);
                        int vmax = Math.min(hi, baseVal + width);
                        JsonObject nodeOut = getOrCreatePath(out, prefix);
                        nodeOut.addProperty("min", vmin);
                        nodeOut.addProperty("max", vmax);
                        return;
                    } else {
                        double lo = pmin.getAsDouble();
                        double hi = pmax.getAsDouble();
                        if (hi <= lo) hi = lo + 1e-6;
                        double span = Math.max(1e-6, hi - lo);
                        double baseVal = randDouble(lo, hi);
                        // increase sampling window to ~30% of span for doubles
                        double maxWidth = Math.max(1e-6, span * 0.30);
                        double width = randDouble(0.0, maxWidth);
                        double vmin = Math.max(lo, baseVal - width);
                        double vmax = Math.min(hi, baseVal + width);
                        JsonObject nodeOut = getOrCreatePath(out, prefix);
                        nodeOut.addProperty("min", vmin);
                        nodeOut.addProperty("max", vmax);
                        return;
                    }
                }
            }
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                List<String> npre = new ArrayList<>(prefix);
                npre.add(e.getKey());
                traverseAndSample(attackKey, npre, e.getValue(), out);
            }
        } else if (node.isJsonPrimitive()) {
            JsonPrimitive prim = node.getAsJsonPrimitive();
            List<String> path = new ArrayList<>(prefix);
            JsonObject nodeOut = getOrCreatePath(out, path.subList(0, Math.max(0, path.size() - 1)));
            if (path.size() == 0) return;
            String lastKey = path.get(path.size() - 1);
            if (prim.isBoolean()) {
                nodeOut.addProperty(lastKey, RNG.nextBoolean());
            } else if (prim.isNumber()) {
                if (isIntegral(prim)) {
                    int base = prim.getAsInt();
                    // broaden the primitive sampling range (more aggressive)
                    int lo = Math.max(0, base / 2);
                    int hi = Math.max(lo + 1, (int) Math.max(base * 2.0, lo + 1));
                    nodeOut.addProperty(lastKey, randInt(lo, hi));
                } else {
                    double base = prim.getAsDouble();
                    double lo = Math.max(0.0, base * 0.5);
                    double hi = Math.max(lo + 1e-6, base * 2.0);
                    nodeOut.addProperty(lastKey, randDouble(lo, hi));
                }
            }
        }
    }

    private static JsonObject getOrCreatePath(JsonObject root, List<String> path) {
        JsonObject cur = root;
        for (String p : path) {
            if (!cur.has(p) || !cur.get(p).isJsonObject()) cur.add(p, new JsonObject());
            cur = cur.getAsJsonObject(p);
        }
        return cur;
    }

    private static JsonObject loadJson(File f) throws IOException {
        try (FileReader fr = new FileReader(f)) {
            return GSON.fromJson(fr, JsonObject.class);
        }
    }

    private static String extractFirstJson(String s) {
        int i = s.indexOf('{');
        if (i < 0) return null;
        int depth = 0;
        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(i, j + 1);
            }
        }
        return null;
    }

    private static boolean isIntegral(JsonPrimitive p) {
        try { p.getAsInt(); return true; } catch (Exception e) { return false; }
    }

    private static int randInt(int lo, int hi) { // inclusive lo..hi
        if (hi <= lo) return lo;
        return lo + RNG.nextInt(hi - lo + 1);
    }

    private static double randDouble(double lo, double hi) {
        if (hi <= lo) return lo;
        return lo + RNG.nextDouble() * (hi - lo);
    }

    private static JsonObject deepMerge(JsonObject base, JsonObject patch) {
        for (Map.Entry<String, JsonElement> e : patch.entrySet()) {
            String k = e.getKey();
            JsonElement v = e.getValue();
            if (v.isJsonObject() && base.has(k) && base.get(k).isJsonObject()) {
                base.add(k, deepMerge(base.getAsJsonObject(k), v.getAsJsonObject()));
            } else {
                base.add(k, v);
            }
        }
        return base;
    }

    // --- hill-climb mutation helpers ---
    private static JsonObject mutateAttackPatch(JsonObject attackSub) {
        JsonObject copy = GSON.fromJson(GSON.toJson(attackSub), JsonObject.class);
        mutateElement(copy);
        return copy;
    }

    private static void mutateElement(JsonElement el) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String key = e.getKey();
                JsonElement child = e.getValue();
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isNumber()) {
                    JsonPrimitive prim = child.getAsJsonPrimitive();
                    if (isIntegral(prim)) {
                        int val = prim.getAsInt();
                        // increase mutation delta to ~30%
                        int delta = Math.max(1, (int) Math.round(Math.abs(val) * 0.30));
                        int newv = val + randInt(-delta, delta);
                        if (newv < 0) newv = 0;
                        obj.addProperty(key, newv);
                    } else {
                        double val = prim.getAsDouble();
                        double delta = Math.abs(val) * 0.30;
                        double newv = val + (RNG.nextDouble() * 2 - 1) * delta;
                        if (newv < 0.0) newv = 0.0;
                        obj.addProperty(key, newv);
                    }
                } else if (child.isJsonObject()) {
                    JsonObject childObj = child.getAsJsonObject();
                    if (childObj.has("min") && childObj.has("max") && childObj.get("min").isJsonPrimitive() && childObj.get("max").isJsonPrimitive()) {
                        JsonPrimitive pmin = childObj.getAsJsonPrimitive("min");
                        JsonPrimitive pmax = childObj.getAsJsonPrimitive("max");
                        if (pmin.isNumber() && pmax.isNumber()) {
                            if (isIntegral(pmin) && isIntegral(pmax)) {
                                int lo = pmin.getAsInt();
                                int hi = pmax.getAsInt();
                                int span = Math.max(1, hi - lo);
                                // make range shifts more aggressive (~30% of span)
                                int shift = randInt(-Math.max(1, span/3), Math.max(1, span/3));
                                int newLo = Math.max(0, lo + shift);
                                int newHi = Math.max(newLo + 1, hi + shift);
                                childObj.addProperty("min", newLo);
                                childObj.addProperty("max", newHi);
                            } else {
                                double lo = pmin.getAsDouble();
                                double hi = pmax.getAsDouble();
                                double span = Math.max(1e-6, hi - lo);
                                double shift = (RNG.nextDouble() * 2 - 1) * span * 0.30;
                                double newLo = Math.max(0.0, lo + shift);
                                double newHi = Math.max(newLo + 1e-6, hi + shift);
                                childObj.addProperty("min", newLo);
                                childObj.addProperty("max", newHi);
                            }
                        }
                        mutateElement(childObj);
                    } else {
                        mutateElement(childObj);
                    }
                } else if (child.isJsonArray()) {
                    // leave arrays unchanged for now
                }
            }
        }
    }

    private static void deleteRecursively(File f) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        if (!f.delete()) {
            // ignore
        }
    }
}
