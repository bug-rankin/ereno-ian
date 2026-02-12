package br.ufu.facom.ereno;

import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIED;
import br.ufu.facom.ereno.attacks.uc04.devices.MasqueradeFakeNormalED;
import br.ufu.facom.ereno.attacks.uc01.devices.RandomReplayerIED;
import br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED;
import br.ufu.facom.ereno.attacks.uc05.devices.InjectorIED;
import br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIED;
import br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIED;
import br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIED;
import br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIED;
import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.benign.uc00.devices.MergingUnit;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.scenarios.InputFilesForSV;

import java.io.IOException;
import java.util.logging.Logger;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import static br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter.*;

public class MultiSource {
    public static int numberOfMessages;

    public static void main(String[] args) throws Exception {
        // Disable Weka's GenericPropertiesCreator to prevent ClassCastException in uber JAR
        System.setProperty("weka.gui.GenericPropertiesCreator.useDynamic", "false");
        
        init();
        numberOfMessages = 10;
        twoDevices("debug", numberOfMessages, false);
//        twoDevices("train", numberOfMessages, true);
//        twoDevices("test", numberOfMessages, false);

//        noIDSDataset("train", numberOfMessages);
//        noIDSDataset("test", numberOfMessages);
//        DatasetEval.runWithoutCV();

    }


    public static void noIDSDataset(String datasetName, int numberOfMessages) throws IOException {
        startWriting("C:\\Users\\lhaid\\ERENO\\datasets" + datasetName + ".csv");

        // Generate SV
        MergingUnit mu = runMU();

        // Generate normal messages
        LegitimateProtectionIED uc00 = new LegitimateProtectionIED();
        uc00.setInitialTimestamp(mu.getInitialTimestamp());
        uc00.run(numberOfMessages);

        RandomReplayerIED uc01 = new RandomReplayerIED(uc00);
        uc01.run(numberOfMessages);

        InverseReplayerIED uc02 = new InverseReplayerIED(uc00);
        uc02.run(numberOfMessages);

        MasqueradeFakeFaultIED uc03 = new MasqueradeFakeFaultIED(uc00);
        uc03.setInitialTimestamp(mu.getInitialTimestamp());
        uc03.run(numberOfMessages);

        MasqueradeFakeNormalED uc04 = new MasqueradeFakeNormalED(uc00);
        uc04.setInitialTimestamp(mu.getInitialTimestamp());
        uc04.run(numberOfMessages);

        InjectorIED uc05 = new InjectorIED(uc00);
        uc05.run(numberOfMessages);

        HighStNumInjectorIED uc06 = new HighStNumInjectorIED(uc00);
        uc06.run(numberOfMessages);

        HighRateStNumInjectorIED uc07 = new HighRateStNumInjectorIED(uc00);
        uc07.run(numberOfMessages);

        ProtectionIED uc00forGrayhole = new LegitimateProtectionIED();
        uc00forGrayhole.setInitialTimestamp(mu.getInitialTimestamp());
        uc00forGrayhole.run((int) (numberOfMessages * 1.2)); // generate 20% more, because 20% will be discarded
        GrayHoleVictimIED uc08 = new GrayHoleVictimIED(uc00forGrayhole);
        uc08.run(80); //80 = discards 20%

        DelayedReplayIED uc10 = new DelayedReplayIED(uc00);
        uc10.run(numberOfMessages);

        uc00.addMessages(uc01.getMessages());
        uc00.addMessages(uc02.getMessages());
        uc00.addMessages(uc03.getMessages());
        uc00.addMessages(uc04.getMessages());
        uc00.addMessages(uc05.getMessages());
        uc00.addMessages(uc06.getMessages());
        uc00.addMessages(uc07.getMessages());
        writeNormal(uc00.getSeedMessage(), uc00.getMessages(), mu.getMessages(), true);

        finishWriting();


    }


    public static void twoDevices(String datasetName, int n, boolean train) throws IOException {
        numberOfMessages = n;
        startWriting("C:\\Users\\lhaid\\ERENO\\datasets\\" + datasetName + ".csv");

        // Generating SV messages
        MergingUnit mu = runMU();

        // Generating GOOSE attacks
        System.out.println("-----------------");
        LegitimateProtectionIED legitimateIED = runUC00(mu, true);


        if (train) {
//            runUC01(legitimateIED, mu);
//            runUC02(legitimateIED, mu);
//            runUC03(legitimateIED, mu);
//            runUC04(legitimateIED, mu);
//            runUC05(legitimateIED, mu);
//            runUC06(legitimateIED, mu);
//            runUC07(legitimateIED, mu);  // parei aqui, os outros parece que tem bug no timestamp
//            runUC08(legitimateIED, mu);
//            runUC10(legitimateIED, mu);
        }
        if (!train) {
//            runUC01(legitimateIED, mu);
//            runUC02(legitimateIED, mu);
//            runUC03(legitimateIED, mu);
            runUC04(legitimateIED, mu);
//            runUC05(legitimateIED, mu);
//            runUC06(legitimateIED, mu);
//            runUC07(legitimateIED, mu);  // parei aqui, os outros parece que tem bug no timestamp
//            runUC08(legitimateIED, mu);
//            runUC10(legitimateIED, mu);
        }

        finishWriting();

    }

    public static MergingUnit runMU() {
        MergingUnit mu = new MergingUnit(InputFilesForSV.electricalSourceFiles);
//        MergingUnit mu = new MergingUnit(Input.singleElectricalSourceFile);
        mu.enableRandomOffsets(numberOfMessages);
        mu.run(numberOfMessages * 4763);
        return mu;
    }

    public static LegitimateProtectionIED runUC00(MergingUnit mu, boolean header) throws IOException {
        LegitimateProtectionIED uc00 = new LegitimateProtectionIED();
        uc00.setInitialTimestamp(mu.getInitialTimestamp());
        Logger.getLogger("RunUC00").info("mu.getOffset(): " + mu.getInitialTimestamp());
        uc00.run(numberOfMessages);
        int qtdNormal00 = writeNormal(uc00.getSeedMessage(), uc00.copyMessages(), mu.getMessages(), header);
        Logger.getLogger("MultiSource").info("Writting " + qtdNormal00 + " legitimate (UC00) messages to dataset.");
        return uc00;
    }

    public static void runUC01(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        RandomReplayerIED uc01 = new RandomReplayerIED(uc00);
        uc01.run(numberOfMessages);
        int qtdReplay01 = writeAttack(uc00, uc01, mu, false);
        Logger.getLogger("MultiSource").info("Writting " + qtdReplay01 + " replayed (UC01) messages to dataset.");
    }

    public static void runUC02(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        InverseReplayerIED uc02 = new InverseReplayerIED(uc00);
        uc02.run(numberOfMessages);
        int qtdReplay02 = writeAttack(uc00, uc02, mu, false);
        Logger.getLogger("MultiSource").info("Writting " + qtdReplay02 + "  replayed (UC02) messages to dataset.");
    }

    // Maybe passing the normal Seed would be benefical to attacker
    public static MasqueradeFakeFaultIED runUC03(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        MasqueradeFakeFaultIED uc03 = new MasqueradeFakeFaultIED(uc00);
        uc03.setInitialTimestamp(mu.getInitialTimestamp());
        Logger.getLogger("RunUC03").info("mu.getOffset(): " + mu.getInitialTimestamp());
        uc03.run(numberOfMessages);
        int msq = writeMasquerade(uc03.getSeedMessage(), uc03.copyMessages(), mu.getMessages(), false);
        Logger.getLogger("MultiSource").info("Writting " + msq + " legitimate (UC00) messages to dataset.");
        return uc03;
    }

    public static MasqueradeFakeNormalED runUC04(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        MasqueradeFakeNormalED uc04 = new MasqueradeFakeNormalED(uc00);
        uc04.setInitialTimestamp(mu.getInitialTimestamp());
        Logger.getLogger("RunUC03").info("mu.getOffset(): " + mu.getInitialTimestamp());
        uc04.run(numberOfMessages);
        int msq = writeMasquerade(uc04.getSeedMessage(), uc04.copyMessages(), mu.getMessages(), false);
        Logger.getLogger("MultiSource").info("Writting " + msq + " legitimate (UC00) messages to dataset.");
        return uc04;
    }


    public static void runUC05(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        InjectorIED uc05 = new InjectorIED(uc00);
        uc05.run(numberOfMessages);
        int qtdInjection05 = writeAttack(uc00, uc05, mu, false);
        Logger.getLogger("MultiSource").info("Writting " + qtdInjection05 + " injected (UC05) messages to dataset.");
    }

    public static void runUC06(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        HighStNumInjectorIED uc06 = new HighStNumInjectorIED(uc00);
        uc06.run(numberOfMessages);
        int qtdInjection06 = writeAttack(uc00, uc06, mu, false);
        Logger.getLogger("MultiSource").info("Writting " + qtdInjection06 + " injected (UC06) messages to dataset.");
    }

    public static void runUC07(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        HighRateStNumInjectorIED uc07 = new HighRateStNumInjectorIED(uc00);
        uc07.run(numberOfMessages);
        int qtdInjection07 = writeAttack(uc00, uc07, mu, false);
        Logger.getLogger("MultiSource").info("Writting " + qtdInjection07 + " injected (UC07) messages to dataset.");
    }

    public static void runUC08(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        ProtectionIED uc00forGrayhole = new LegitimateProtectionIED();
        uc00forGrayhole.setInitialTimestamp(mu.getInitialTimestamp());
        uc00forGrayhole.run((int) (numberOfMessages * 1.2)); // generate 20% more, because 20% will be discarded
        GrayHoleVictimIED uc08 = new GrayHoleVictimIED(uc00forGrayhole);
        uc08.run(80); //80 = discards 20%
        int qtdGrayhole08 = writeAttack(uc00, uc08, mu, false);
        Logger.getLogger("MultiSource").info("Writting " + qtdGrayhole08 + " gryhole (UC08) messages to dataset.");
    }

    public static void runUC10(LegitimateProtectionIED uc00, MergingUnit mu) throws IOException {
        DelayedReplayIED uc10 = new DelayedReplayIED(uc00);
        uc10.run(numberOfMessages);
        int qtdReplay10 = writeAttack(uc00, uc10, mu, false);
        Logger.getLogger("MultiSource").info("Writing " + qtdReplay10 + " delayed (UC10) messages to dataset.");
    }


    public static void init() {
        try {
            ConfigLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // defaults (can be overridden in config file)
        ConfigLoader.attacks.legitimate = true;
        ConfigLoader.attacks.randomReplay = false;
        ConfigLoader.attacks.masqueradeOutage = true;
        ConfigLoader.attacks.masqueradeDamage = true;
        ConfigLoader.attacks.randomInjection = true;
        ConfigLoader.attacks.inverseReplay = false;
        ConfigLoader.attacks.highStNum = false;
        ConfigLoader.attacks.flooding = false;
        ConfigLoader.attacks.grayhole = false;
        ConfigLoader.attacks.delayedReplay = true;
        numberOfMessages = ConfigLoader.gooseFlow.numberOfMessages;
    }


}
