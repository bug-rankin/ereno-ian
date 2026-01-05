package br.ufu.facom.ereno;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import br.ufu.facom.ereno.attacks.uc01.devices.RandomReplayerIED;
import br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIED;
import br.ufu.facom.ereno.attacks.uc05.devices.InjectorIED;
import br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIED;
import br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIED;
import br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIED;
import br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIED;
import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.benign.uc00.devices.MergingUnit;
import br.ufu.facom.ereno.config.ConfigLoader;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.startWriting;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.write;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.writeGooseMessagesToFile;
import static br.ufu.facom.ereno.dataExtractors.DatasetWriter.writeSvMessagesToFile;
import static br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter.finishWriting;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.messages.Sv;
import br.ufu.facom.ereno.util.BenignDataManager;

public class SingleSource {

    public static void main(String[] args) throws IOException {
        // Disable Weka's GenericPropertiesCreator to prevent ClassCastException in uber JAR
        System.setProperty("weka.gui.GenericPropertiesCreator.useDynamic", "false");
        
        ConfigLoader.load();

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

        SingleSource.lightweightDataset(System.getProperty("user.dir") + java.io.File.separator + "target" + java.io.File.separator + "ereno_generated.csv", true, "csv");
    }


    public static void lightweightDataset(String datasetOutputLocation, boolean generateSV, String format) throws IOException {
        long beginTime = System.currentTimeMillis();

        Logger.getLogger("Extractor").info(() -> datasetOutputLocation + " writting...");
        boolean csvMode = "csv".equalsIgnoreCase(format);

        // Start writing using the appropriate writer
        if (csvMode) {
            br.ufu.facom.ereno.dataExtractors.CSVWritter.startWriting(datasetOutputLocation);
        } else {
            startWriting(datasetOutputLocation);
            write("@relation ereno_lightweight");
        }

        int totalMessageCount = 0;

        // Generate or load benign data
        LegitimateProtectionIED uc00 = null;
        if (ConfigLoader.attacks.legitimate) {
            // Check if we should import existing benign data
            if (ConfigLoader.benignData.importBenignDataPath != null && 
                !ConfigLoader.benignData.importBenignDataPath.trim().isEmpty()) {
                // Load benign data from file
                Logger.getLogger("BenignData").info(() -> "Loading benign data from: " + 
                    ConfigLoader.benignData.importBenignDataPath);
                uc00 = BenignDataManager.loadBenignData(ConfigLoader.benignData.importBenignDataPath);
                final LegitimateProtectionIED finalUc00 = uc00;
                Logger.getLogger("BenignData").info(() -> "Loaded " + finalUc00.getNumberOfMessages() + 
                    " benign messages from file.");
            } else {
                // Generate new benign data
                Logger.getLogger("BenignData").info("Generating new benign data...");
                uc00 = new LegitimateProtectionIED();
                uc00.run(1000);
                
                // Save benign data if configured
                if (ConfigLoader.benignData.saveBenignData) {
                    Logger.getLogger("BenignData").info("Saving benign data...");
                    BenignDataManager.saveBenignData(uc00.copyMessages(), format);
                }
            }
            
            // Write benign data to the main dataset
            if (csvMode) {
                writeGooseMessagesCsv(uc00.copyMessages(), true);
            } else {
                writeGooseMessagesToFile(uc00.copyMessages(), true);
            }
            totalMessageCount += uc00.getNumberOfMessages();
        }

        RandomReplayerIED uc01;
        if (ConfigLoader.attacks.randomReplay) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc01.devices.RandomReplayerIED uc01c = new br.ufu.facom.ereno.attacks.uc01.devices.RandomReplayerIED(uc00);
                uc01c.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc01c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc01c.getMessages(), false);
                }
                totalMessageCount += uc01c.getNumberOfMessages();
            } else {
                uc01 = new RandomReplayerIED(uc00);
                uc01.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc01.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc01.getMessages(), false);
                }
                totalMessageCount += uc01.getNumberOfMessages();
            }
        }

        if (ConfigLoader.attacks.inverseReplay) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED uc02c = new br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED(uc00);
                uc02c.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc02c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc02c.getMessages(), false);
                }
                totalMessageCount += uc02c.getNumberOfMessages();
            } else {
                br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED uc02 = new br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED(uc00);
                uc02.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc02.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc02.getMessages(), false);
                }
                totalMessageCount += uc02.getNumberOfMessages();
            }
        }

        if (ConfigLoader.attacks.masqueradeOutage) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIED uc03c = new br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIED(uc00);
                uc03c.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc03c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc03c.getMessages(), false);
                }
                totalMessageCount += uc03c.getNumberOfMessages();
            } else {
                MasqueradeFakeFaultIED uc03 = new MasqueradeFakeFaultIED(uc00);
                uc03.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc03.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc03.getMessages(), false);
                }
                totalMessageCount += uc03.getNumberOfMessages();
            }
        }

        if (ConfigLoader.attacks.randomInjection) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc05.devices.InjectorIED uc05c = new br.ufu.facom.ereno.attacks.uc05.devices.InjectorIED(uc00);
                uc05c.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc05c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc05c.getMessages(), false);
                }
                totalMessageCount += uc05c.getNumberOfMessages();
            } else {
                InjectorIED uc05 = new InjectorIED(uc00);
                uc05.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc05.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc05.getMessages(), false);
                }
                totalMessageCount += uc05.getNumberOfMessages();
            }
        }

        if (ConfigLoader.attacks.highStNum) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIED uc06c = new br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIED(uc00);
                uc06c.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc06c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc06c.getMessages(), false);
                }
                totalMessageCount += uc06c.getNumberOfMessages();
            } else {
                HighStNumInjectorIED uc06 = new HighStNumInjectorIED(uc00);
                uc06.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc06.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc06.getMessages(), false);
                }
                totalMessageCount += uc06.getNumberOfMessages();
            }
        }

        if (ConfigLoader.attacks.flooding) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIED uc07c = new br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIED(uc00);
                uc07c.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc07c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc07c.getMessages(), false);
                }
                totalMessageCount += uc07c.getNumberOfMessages();
            } else {
                HighRateStNumInjectorIED uc07 = new HighRateStNumInjectorIED(uc00);
                uc07.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc07.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc07.getMessages(), false);
                }
                totalMessageCount += uc07.getNumberOfMessages();
            }
        }

        if (ConfigLoader.attacks.grayhole) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIED uc08c = new br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIED(uc00);
                uc08c.run(20);
                if (csvMode) {
                    writeGooseMessagesCsv(uc08c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc08c.getMessages(), false);
                }
                totalMessageCount += uc08c.getNumberOfMessages();
            } else {
                GrayHoleVictimIED uc08 = new GrayHoleVictimIED(uc00);
                uc08.run(20);
                if (csvMode) {
                    writeGooseMessagesCsv(uc08.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc08.getMessages(), false);
                }
                totalMessageCount += uc08.getNumberOfMessages();
            }
        }

        if (ConfigLoader.attacks.delayedReplay) {
            if (ConfigLoader.devices.useCVariants) {
                br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIED uc10c = new br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIED(uc00);
                uc10c.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc10c.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc10c.getMessages(), false);
                }
                totalMessageCount += uc10c.getNumberOfMessages();
            } else {
                DelayedReplayIED uc10 = new DelayedReplayIED(uc00);
                uc10.run(ConfigLoader.gooseFlow.numberOfMessages);
                if (csvMode) {
                    writeGooseMessagesCsv(uc10.getMessages(), false);
                } else {
                    writeGooseMessagesToFile(uc10.getMessages(), false);
                }
            }
        }

        // Finish writing
        if (csvMode) {
            br.ufu.facom.ereno.dataExtractors.CSVWritter.finishWriting();
        } else {
            finishWriting();
        }

        long endTime = System.currentTimeMillis();
        final int finalMessageCount = totalMessageCount;
        Logger.getLogger("Time").info(() -> "Tempo gasto para gerar " + finalMessageCount + " mensagens: " + (endTime - beginTime));
    }

    private static void writeGooseMessagesCsv(ArrayList<Goose> gooseMessages, boolean printHeader) throws IOException {
        // This follows similar logic as DatasetWriter.writeGooseMessagesToFile but writes CSV lines via CSVWritter
        Goose prev = null;
        if (printHeader) {
            br.ufu.facom.ereno.dataExtractors.CSVWritter.writeDefaultHeader();
        }
        for (Goose gm : gooseMessages) {
            if (prev != null) { // skips the first message
                Sv svResolved = null;
                String svString = svResolved == null ? "" : svResolved.asCsv();
                String cycleStrig = "";
                String gooseString = gm.asCSVFull();
                String gooseConsistency = br.ufu.facom.ereno.featureEngineering.IntermessageCorrelation.getConsistencyFeaturesAsCSV(gm, prev);
                double delay = gm.getTimestamp() - (svResolved == null ? gm.getTimestamp() : svResolved.getTime());
                String line = svString + "," + cycleStrig + "," + gooseString + "," + gooseConsistency + "," + delay + "," + gm.getLabel();
                br.ufu.facom.ereno.dataExtractors.CSVWritter.writeLine(line);
            }
            prev = gm.copy();
        }
    }

    public static void scriptForGooseAndSV(String datasetOutputLocation, boolean generateSV) throws IOException { // Generates only GOOSE data
        long beginTime = System.currentTimeMillis();

        // Start writing
        Logger.getLogger("Extractor").info(() -> datasetOutputLocation + " writting...");
        startWriting(datasetOutputLocation);
        int totalMessageCount = 0;
        write("@relation goose_traffic");

        // Generate and write samples for legitimate and attacks chosen in config
        LegitimateProtectionIED uc00 = null;
        if (ConfigLoader.attacks.legitimate) {
            uc00 = new LegitimateProtectionIED();
            uc00.run(1000);
            writeGooseMessagesToFile(uc00.copyMessages(), true);
            totalMessageCount += uc00.getNumberOfMessages();
        }

        RandomReplayerIED uc01;
        if (ConfigLoader.attacks.randomReplay) {
            uc01 = new RandomReplayerIED(uc00);
            uc01.run(ConfigLoader.gooseFlow.numberOfMessages);
            writeGooseMessagesToFile(uc01.getMessages(), false);
            totalMessageCount += uc01.getNumberOfMessages();
        }

        if (ConfigLoader.attacks.inverseReplay) {
            br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED uc02c = new br.ufu.facom.ereno.attacks.uc02.devices.InverseReplayerIED(uc00);
            uc02c.run(ConfigLoader.gooseFlow.numberOfMessages);
            writeGooseMessagesToFile(uc02c.getMessages(), false);
            totalMessageCount += uc02c.getNumberOfMessages();
        }

        if (ConfigLoader.attacks.masqueradeOutage) {
            br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIED uc03c = new br.ufu.facom.ereno.attacks.uc03.devices.MasqueradeFakeFaultIED(uc00);
            uc03c.run(ConfigLoader.gooseFlow.numberOfMessages);
            writeGooseMessagesToFile(uc03c.getMessages(), false);
            totalMessageCount += uc03c.getNumberOfMessages();
        }

        if (ConfigLoader.attacks.randomInjection) {
            br.ufu.facom.ereno.attacks.uc05.devices.InjectorIED uc05c = new br.ufu.facom.ereno.attacks.uc05.devices.InjectorIED(uc00);
            uc05c.run(ConfigLoader.gooseFlow.numberOfMessages);
            writeGooseMessagesToFile(uc05c.getMessages(), false);
            totalMessageCount += uc05c.getNumberOfMessages();
        }

        if (ConfigLoader.attacks.highStNum) {
            br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIED uc06c = new br.ufu.facom.ereno.attacks.uc06.devices.HighStNumInjectorIED(uc00);
            uc06c.run(ConfigLoader.gooseFlow.numberOfMessages);
            writeGooseMessagesToFile(uc06c.getMessages(), false);
            totalMessageCount += uc06c.getNumberOfMessages();
        }

        if (ConfigLoader.attacks.flooding) {
            br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIED uc07c = new br.ufu.facom.ereno.attacks.uc07.devices.HighRateStNumInjectorIED(uc00);
            uc07c.run(ConfigLoader.gooseFlow.numberOfMessages);
            writeGooseMessagesToFile(uc07c.getMessages(), false);
            totalMessageCount += uc07c.getNumberOfMessages();
        }

        if (ConfigLoader.attacks.grayhole) {
            br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIED uc08c = new br.ufu.facom.ereno.attacks.uc08.devices.GrayHoleVictimIED(uc00);
            uc08c.run(20);
            writeGooseMessagesToFile(uc08c.getMessages(), false);
            totalMessageCount += uc08c.getNumberOfMessages();
        }

        if (ConfigLoader.attacks.delayedReplay) {
            br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIED uc10c = new br.ufu.facom.ereno.attacks.uc10.devices.DelayedReplayIED(uc00);
            uc10c.run(ConfigLoader.gooseFlow.numberOfMessages);
            writeGooseMessagesToFile(uc10c.getMessages(), false);
            totalMessageCount += uc10c.getNumberOfMessages();
        }

        finishWriting();
        long endTime = System.currentTimeMillis();
        final int finalTotalMessageCount = totalMessageCount;
        Logger.getLogger("Time").info(() -> "Tempo gasto para gerar " + finalTotalMessageCount + " mensagens: " + (endTime - beginTime));
    }

    public static void scriptForSV(String[] svData, String datasetLocation) throws IOException { // Generates only SV data
        MergingUnit mu = new MergingUnit(svData);
        mu.run(4800); // setup here how many lines will be consumed by messages SV (setting a very large number will use all available SV data)
        startWriting(datasetLocation);
        writeSvMessagesToFile(mu.getMessages(), true, "sb");
        finishWriting();
    }

}


