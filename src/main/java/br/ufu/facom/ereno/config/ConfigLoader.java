package br.ufu.facom.ereno.config;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.FileReader;
import java.io.IOException;

/**
 * Simple configuration loader for the merged config file `config/configparams.json`.
 * It exposes static fields that the runners can read (and modify at runtime if needed).
 */
public class ConfigLoader {

    public static AttacksConfig attacks = new AttacksConfig();
    public static AttacksParams attacksParams = new AttacksParams();
    public static GooseFlowConfig gooseFlow = new GooseFlowConfig();
    public static SetupIEDConfig setupIED = new SetupIEDConfig();
    public static OutputConfig output = new OutputConfig();
    public static DevicesConfig devices = new DevicesConfig();
    public static BenignDataConfig benignData = new BenignDataConfig();
    // optional global seed for deterministic runs (null -> use system time)
    public static Long randomSeed = null;
    // shared RNG used across the codebase
    public static java.util.Random RNG = new java.util.Random(System.nanoTime());

    public static void load() throws IOException {
        load("config/configparams.json");
    }

    public static void load(String path) throws IOException {
        try (FileReader r = new FileReader(path)) {
            Gson g = new Gson();
            Holder h = g.fromJson(r, Holder.class);
            if (h != null) {
                if (h.attacks != null) attacks = h.attacks;
                if (h.gooseFlow != null) gooseFlow = h.gooseFlow;
                if (h.setupIED != null) setupIED = h.setupIED;
                if (h.output != null) output = h.output;
                if (h.attacksParams != null) attacksParams = h.attacksParams;
                // sanitize and fill sensible defaults for any ranges that may be missing
                sanitizeDefaults();
                if (h.devices != null) devices = h.devices;
                if (h.benignData != null) benignData = h.benignData;
                // load optional global seed
                if (h.randomSeed != null) {
                    randomSeed = h.randomSeed;
                    RNG = new java.util.Random(randomSeed);
                } else {
                    RNG = new java.util.Random(System.nanoTime());
                }
            }
        }
    }

    /**
     * Defensive post-processing after JSON load: some older/partial configs may omit
     * the legacy UC* fields or leave numeric ranges at zero. Ensure we have sensible
     * non-zero ranges to avoid runtime exceptions (e.g. IED.randomBetween).
     */
    private static void sanitizeDefaults() {
        if (attacksParams == null) return;
        StringBuilder applied = new StringBuilder();

        // UC02 time range used by inverse-replay creators. Ensure min < max.
        if (attacksParams.uc02 == null) attacksParams.uc02 = new AttacksParams.UC02Config();
        if (attacksParams.uc02.timeTakenMinMs == null) attacksParams.uc02.timeTakenMinMs = new AttacksParams.RangeMs();
        int uc02min = attacksParams.uc02.timeTakenMinMs.minMs;
        int uc02max = attacksParams.uc02.timeTakenMinMs.maxMs;
        if (uc02min <= 0 || uc02max <= 0 || uc02min >= uc02max) {
            attacksParams.uc02.timeTakenMinMs.minMs = 300;   // 0.3s
            attacksParams.uc02.timeTakenMinMs.maxMs = 5000;  // 5s
            attacksParams.uc02.timeTakenMinMs.defaultMs = 1000;
            applied.append("uc02.timeTakenMinMs set to 300..5000 ms; ");
        }

        // UC01: short/long attacker time ranges
        if (attacksParams.uc01 == null) attacksParams.uc01 = new AttacksParams.UC01Config();
        if (attacksParams.uc01.timeTakenShort == null) attacksParams.uc01.timeTakenShort = new AttacksParams.RangeMs();
        if (attacksParams.uc01.timeTakenLong == null) attacksParams.uc01.timeTakenLong = new AttacksParams.RangeMs();
        if (attacksParams.uc01.longChancePercent == null) attacksParams.uc01.longChancePercent = new AttacksParams.RangeInt();
        if (attacksParams.uc01.timeTakenShort.minMs <= 0 || attacksParams.uc01.timeTakenShort.maxMs <= 0 ||
                attacksParams.uc01.timeTakenShort.minMs >= attacksParams.uc01.timeTakenShort.maxMs) {
            attacksParams.uc01.timeTakenShort.minMs = 50;
            attacksParams.uc01.timeTakenShort.maxMs = 500;
            attacksParams.uc01.timeTakenShort.defaultMs = 100;
            applied.append("uc01.timeTakenShort set to 50..500 ms; ");
        }
        if (attacksParams.uc01.timeTakenLong.minMs <= 0 || attacksParams.uc01.timeTakenLong.maxMs <= 0 ||
                attacksParams.uc01.timeTakenLong.minMs >= attacksParams.uc01.timeTakenLong.maxMs) {
            attacksParams.uc01.timeTakenLong.minMs = 500;
            attacksParams.uc01.timeTakenLong.maxMs = 5000;
            attacksParams.uc01.timeTakenLong.defaultMs = 1500;
            applied.append("uc01.timeTakenLong set to 500..5000 ms; ");
        }
        if (attacksParams.uc01.longChancePercent.min <= 0 || attacksParams.uc01.longChancePercent.max <= 0 ||
                attacksParams.uc01.longChancePercent.min >= attacksParams.uc01.longChancePercent.max) {
            attacksParams.uc01.longChancePercent.min = 5;
            attacksParams.uc01.longChancePercent.max = 50;
            attacksParams.uc01.longChancePercent.defaultValue = 15;
            applied.append("uc01.longChancePercent set to 5..50%; ");
        }

        // UC03: masquerade fault defaults
        if (attacksParams.uc03 == null) attacksParams.uc03 = new AttacksParams.UC03Config();
        if (attacksParams.uc03.faultProbability == null) attacksParams.uc03.faultProbability = new AttacksParams.RangeInt();
        if (attacksParams.uc03.networkDelayMs == null) attacksParams.uc03.networkDelayMs = new AttacksParams.RangeMs();
        if (attacksParams.uc03.faultProbability.min <= 0 || attacksParams.uc03.faultProbability.max <= 0 ||
                attacksParams.uc03.faultProbability.min >= attacksParams.uc03.faultProbability.max) {
            attacksParams.uc03.faultProbability.min = 1;
            attacksParams.uc03.faultProbability.max = 100;
            attacksParams.uc03.faultProbability.defaultValue = 60;
            applied.append("uc03.faultProbability set to 1..100%; ");
        }
        if (attacksParams.uc03.networkDelayMs.minMs <= 0 || attacksParams.uc03.networkDelayMs.maxMs <= 0 ||
                attacksParams.uc03.networkDelayMs.minMs >= attacksParams.uc03.networkDelayMs.maxMs) {
            attacksParams.uc03.networkDelayMs.minMs = 10;
            attacksParams.uc03.networkDelayMs.maxMs = 1000;
            attacksParams.uc03.networkDelayMs.defaultMs = 100;
            applied.append("uc03.networkDelayMs set to 10..1000 ms; ");
        }

        // UC05 / UC06: stNum jump ranges
        if (attacksParams.uc05 == null) attacksParams.uc05 = new AttacksParams.UC05Config();
        if (attacksParams.uc05.stNumMin == null) attacksParams.uc05.stNumMin = new AttacksParams.RangeInt();
        if (attacksParams.uc05.stNumMax == null) attacksParams.uc05.stNumMax = new AttacksParams.RangeInt();
        if (attacksParams.uc05.stNumMin.min <= 0 || attacksParams.uc05.stNumMax.max <= 0 ||
                attacksParams.uc05.stNumMin.min >= attacksParams.uc05.stNumMax.max) {
            attacksParams.uc05.stNumMin.min = 1;
            attacksParams.uc05.stNumMax.max = 200;
            applied.append("uc05.stNum set to 1..200; ");
        }

        if (attacksParams.uc06 == null) attacksParams.uc06 = new AttacksParams.UC06Config();
        if (attacksParams.uc06.stNumMin == null) attacksParams.uc06.stNumMin = new AttacksParams.RangeInt();
        if (attacksParams.uc06.stNumMax == null) attacksParams.uc06.stNumMax = new AttacksParams.RangeInt();
        if (attacksParams.uc06.stNumMin.min <= 0 || attacksParams.uc06.stNumMax.max <= 0 ||
                attacksParams.uc06.stNumMin.min >= attacksParams.uc06.stNumMax.max) {
            attacksParams.uc06.stNumMin.min = 1;
            attacksParams.uc06.stNumMax.max = 200;
            applied.append("uc06.stNum set to 1..200; ");
        }

        // UC07: multiplier
        if (attacksParams.uc07 == null) attacksParams.uc07 = new AttacksParams.UC07Config();
        if (attacksParams.uc07.minTimeMultiplier == null) attacksParams.uc07.minTimeMultiplier = new AttacksParams.RangeDouble();
        if (attacksParams.uc07.minTimeMultiplier.min <= 0 || attacksParams.uc07.minTimeMultiplier.max <= 0 ||
                attacksParams.uc07.minTimeMultiplier.min >= attacksParams.uc07.minTimeMultiplier.max) {
            attacksParams.uc07.minTimeMultiplier.min = 0.1;
            attacksParams.uc07.minTimeMultiplier.max = 5.0;
            attacksParams.uc07.minTimeMultiplier.defaultValue = 1.0;
            applied.append("uc07.minTimeMultiplier set to 0.1..5.0; ");
        }

        // UC08: selection rate
        if (attacksParams.uc08 == null) attacksParams.uc08 = new AttacksParams.UC08Config();
        if (attacksParams.uc08.selectionRate == null) attacksParams.uc08.selectionRate = new AttacksParams.RangeInt();
        if (attacksParams.uc08.selectionRate.min <= 0 || attacksParams.uc08.selectionRate.max <= 0 ||
                attacksParams.uc08.selectionRate.min >= attacksParams.uc08.selectionRate.max) {
            attacksParams.uc08.selectionRate.min = 1;
            attacksParams.uc08.selectionRate.max = 100;
            attacksParams.uc08.selectionRate.defaultValue = 10;
            applied.append("uc08.selectionRate set to 1..100%; ");
        }

        if (attacksParams.uc10 == null) attacksParams.uc10 = new AttacksParams.UC10Config();
        // add stuff here after asking Ian what the heck this does.
        if (attacksParams.uc10.selectionInterval == null) attacksParams.uc10.selectionInterval = new AttacksParams.RangeInt();
        if (attacksParams.uc10.selectionInterval.min <= 0 || attacksParams.uc10.selectionInterval.max <= 0 ||
        attacksParams.uc10.selectionInterval.min >= attacksParams.uc10.selectionInterval.max) {
            attacksParams.uc10.selectionInterval.min = 5;
            attacksParams.uc10.selectionInterval.max = 25;
            attacksParams.uc10.selectionInterval.defaultValue = 15;
        }
        if (attacksParams.uc10.burstInterval == null)  attacksParams.uc10.burstInterval = new AttacksParams.RangeInt();
        if (attacksParams.uc10.burstInterval.min <= 0 || attacksParams.uc10.burstInterval.max <= 0 ||
        attacksParams.uc10.burstInterval.min >= attacksParams.uc10.burstInterval.max) {
            attacksParams.uc10.burstInterval.min = 5;
            attacksParams.uc10.burstInterval.max = 25;
            attacksParams.uc10.burstInterval.defaultValue = 15;
        }
        if (attacksParams.uc10.burstSize == null)  attacksParams.uc10.burstSize = new AttacksParams.RangeInt();
        if (attacksParams.uc10.burstSize.min <= 0 || attacksParams.uc10.burstSize.max <= 0 ||
        attacksParams.uc10.burstSize.min >= attacksParams.uc10.burstSize.max) {
            attacksParams.uc10.burstSize.min = 5;
            attacksParams.uc10.burstSize.max = 25;
            attacksParams.uc10.burstSize.defaultValue = 15;
        }


        // Log applied defaults (if any) to help auditing and debugging of config migrations
        if (applied.length() > 0) {
            java.util.logging.Logger.getLogger("ConfigLoader").info("Applied config defaults: " + applied.toString());
        } else {
            java.util.logging.Logger.getLogger("ConfigLoader").info("No config defaults applied; config looked healthy.");
        }
    }

    private static class Holder {
        AttacksConfig attacks;
        @SerializedName("gooseFlow")
        GooseFlowConfig gooseFlow;
        @SerializedName("setupIED")
        SetupIEDConfig setupIED;
        Long randomSeed;
        OutputConfig output;
        AttacksParams attacksParams;
        DevicesConfig devices;
        BenignDataConfig benignData;
    }

    public static class AttacksParams {
        // Backwards-compatible UC* slots (kept to avoid breaking older code)
        public UC01Config uc01 = new UC01Config();
        public UC02Config uc02 = new UC02Config();
        public UC03Config uc03 = new UC03Config();
        public UC05Config uc05 = new UC05Config();
        public UC06Config uc06 = new UC06Config();
        public UC07Config uc07 = new UC07Config();
        public UC08Config uc08 = new UC08Config();
        public UC10Config uc10 = new UC10Config();

        // New, descriptive attack parameter sections (match keys in config/configparams.json)
        public RandomReplayConfig randomReplay = new RandomReplayConfig();
        public InverseReplayConfig inverseReplay = new InverseReplayConfig();
        public MasqFaultConfig masqFault = new MasqFaultConfig();
        public MasqNormalConfig masqNormal = new MasqNormalConfig();
        public HighStNumInjectionConfig highStNumInjection = new HighStNumInjectionConfig();
        public HighRateStNumInjectionConfig highRateStNumInjection = new HighRateStNumInjectionConfig();
        public GreyholeConfig greyhole = new GreyholeConfig();
        public DelayedReplayConfig delayedRandomReplay = new DelayedReplayConfig();

        public static class RangeInt { public int min; public int max; public int defaultValue; }
        public static class RangeMs { public int minMs; public int maxMs; public int defaultMs; }
        public static class RangeDouble { public double min; public double max; public double defaultValue; }

        // --- old UC* configs (unchanged) ---
        public static class UC01Config {
            public RangeMs timeTakenShort = new RangeMs();
            public RangeMs timeTakenLong = new RangeMs();
            public RangeInt longChancePercent = new RangeInt();
        }

        public static class UC02Config {
            public RangeMs timeTakenMinMs = new RangeMs();
            public UC02Config() {
                // sensible defaults: 0.3s .. 5s (expressed in ms)
                this.timeTakenMinMs.minMs = 300;
                this.timeTakenMinMs.maxMs = 5000;
                this.timeTakenMinMs.defaultMs = 1000;
            }
        }

        public static class UC03Config { public RangeInt faultProbability = new RangeInt(); public RangeMs networkDelayMs = new RangeMs(); }

        public static class UC05Config { public RangeInt stNumMin = new RangeInt(); public RangeInt stNumMax = new RangeInt(); }

        public static class UC06Config { public RangeInt stNumMin = new RangeInt(); public RangeInt stNumMax = new RangeInt(); }

        public static class UC07Config { public RangeDouble minTimeMultiplier = new RangeDouble(); }

        public static class UC08Config { public RangeInt selectionRate = new RangeInt(); }

        public static class UC10Config { // adjust the config if necessary
            public double selectionProb = 0.5;
            public RangeInt selectionInterval = new RangeInt();
            public RangeInt burstInterval = new RangeInt();
            public RangeInt burstSize = new RangeInt();
            public boolean burstMode = false;
        }

        // --- new descriptive configs ---
        public static class RandomReplayConfig {
            public boolean enabled = true;
            public CountConfig count = new CountConfig();
            public RangeDouble windowS = new RangeDouble();
            public RangeDouble delayMs = new RangeDouble();
            public BurstConfig burst = new BurstConfig();
            public double reorderProb = 0.15;
            public TTLOverrideConfig ttlOverride = new TTLOverrideConfig();
            public EthSpoofConfig ethSpoof = new EthSpoofConfig();
            public static class CountConfig { public int lambda = 800; }
            public static class BurstConfig { public double prob = 0.35; public int min = 20; public int max = 40; public RangeDouble gapMs = new RangeDouble(); }
            public static class TTLOverrideConfig { public int[] valuesMs = new int[] {40,10,2,0}; public double prob = 0.2; }
            public static class EthSpoofConfig { public double srcProb = 0.1; public double dstProb = 0.05; }
        }

        public static class InverseReplayConfig {
            public boolean enabled = true;
            public CountConfig count = new CountConfig();
            public RangeInt blockLen = new RangeInt();
            public RangeDouble delayMs = new RangeDouble();
            public BurstConfig burst = new BurstConfig();
            public TTLOverrideConfig ttlOverride = new TTLOverrideConfig();
            public static class CountConfig { public int lambda = 600; }
            public static class BurstConfig { public double prob = 0.25; public int min = 5; public int max = 15; public RangeDouble gapMs = new RangeDouble(); }
            public static class TTLOverrideConfig { public int[] valuesMs = new int[] {500,1500}; public double prob = 0.15; }
        }

        public static class MasqFaultConfig {
            public boolean enabled = true;
            public FaultConfig fault = new FaultConfig();
            public int cbStatus = 1;
            public boolean incrementStNumOnFault = true;
            public String sqnumMode = "fast";
            public int[] ttlMsValues = new int[] {20,40,80};
            public AnalogConfig analog = new AnalogConfig();
            public TrapAreaConfig trapArea = new TrapAreaConfig();
            public static class FaultConfig { public double prob = 0.6; public RangeInt durationMs = new RangeInt(); }
            public static class AnalogConfig { public RangeDouble deltaAbs = new RangeDouble(); }
            public static class TrapAreaConfig { public RangeDouble multiplier = new RangeDouble(); public double spikeProb = 0.5; }
        }

        public static class MasqNormalConfig {
            public boolean enabled = true;
            public boolean clearCbStatus = true;
            public double suppressStNumChangesProb = 0.7;
            public String sqnumMode = "slow";
            public SmoothingConfig smoothing = new SmoothingConfig();
            public int ttlMs = 1000;
            public static class SmoothingConfig { public int window = 5; public double alpha = 0.6; }
        }

        public static class HighStNumInjectionConfig {
            public boolean enabled = true;
            public CountConfig count = new CountConfig();
            public JumpConfig jump = new JumpConfig();
            public double sqnumResetProb = 0.4;
            public TTLOverrideConfig ttlOverride = new TTLOverrideConfig();
            public PadBytesConfig padBytes = new PadBytesConfig();
            public static class CountConfig { public int lambda = 400; }
            public static class JumpConfig { public int min = 5; public int max = 200; }
            public static class TTLOverrideConfig { public int[] valuesMs = new int[] {100,200}; public double prob = 0.3; }
            public static class PadBytesConfig { public int min = 0; public int max = 24; }
        }

        public static class HighRateStNumInjectionConfig {
            public boolean enabled = true;
            public BurstConfig burst = new BurstConfig();
            public RangeDouble gapMs = new RangeDouble();
            public boolean stnumEveryPacket = true;
            public int[] sqnumStrideValues = new int[] {1,2,4};
            public int ttlMs = 20;
            public double ethSrcSpoofProb = 0.2;
            public static class BurstConfig { public int min = 15; public int max = 200; }
        }

        public static class GreyholeConfig {
            public boolean enabled = false;
            public RangeDouble dropRate = new RangeDouble();
            public double burstDropProb = 0.3;
            public RangeDouble burstDropLen = new RangeDouble();
            public RangeDouble extraDelayMs = new RangeDouble();
            public double delayBurstProb = 0.2;
            public RangeDouble delayBurstLen = new RangeDouble();
        }

        public static class DelayedReplayConfig { // adjust if necessary
            public boolean enabled = false;
            public RangeInt selectionInterval = new RangeInt();
            public RangeInt burstInterval = new RangeInt();
            public RangeInt burstSize = new RangeInt();
            public double selectionProb = 0.5;
            public boolean burstMode = false;
        }

    }

    public static class OutputConfig {
        /** "arff" or "csv" (case-insensitive). Defaults to arff. */
        public String format = "arff";
        /** Optional filename. If null, the runner will choose a sane default inside target/. */
        public String filename = null;
    }

    public static class AttacksConfig {
        public String datasetName;
        public boolean legitimate;
        public boolean randomReplay;
        public boolean masqueradeOutage;
        public boolean masqueradeDamage;
        public boolean randomInjection;
        public boolean inverseReplay;
        public boolean highStNum;
        public boolean flooding;
        public boolean grayhole;
        public boolean delayedReplay;
        public boolean stealthyAttack;
    }

    public static class GooseFlowConfig {
        public String goID;
        public int numberOfMessages;
        public String ethSrc;
        public String ethDst;
        public String ethType;
        public String gooseAppid;
        public String TPID;
        public boolean ndsCom;
        public boolean test;
        public boolean cbstatus;
    }

    public static class SetupIEDConfig {
        public String iedName;
        public String gocbRef;
        public String datSet;
        public String minTime;
        public String maxTime;
        public String timestamp;
        public String stNum;
        public String sqNum;
    }

    public static class DevicesConfig {
        /** If true, instantiate the non-destructive C-variant devices (default true). If false, instantiate legacy device classes. */
        public boolean useCVariants = true;
    }

    public static class BenignDataConfig {
        /** Directory where benign datasets are saved (default: target/benign_data). */
        public String benignDataDir = "target/benign_data";
        /** If true, save generated benign data to a file before generating attacks. */
        public boolean saveBenignData = false;
        /** If specified, import benign data from this file instead of generating new data. */
        public String importBenignDataPath = null;
        /** Fault probability percentage used in benign data generation (for filename). Default is 5. */
        public int faultProbability = 5;
    }
}
