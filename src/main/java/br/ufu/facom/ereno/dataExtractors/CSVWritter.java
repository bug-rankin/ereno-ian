package br.ufu.facom.ereno.dataExtractors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.logging.Logger;

import br.ufu.facom.ereno.featureEngineering.IntermessageCorrelation;
import br.ufu.facom.ereno.featureEngineering.ProtocolCorrelation;
import br.ufu.facom.ereno.messages.EthernetFrame;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.messages.Sv;

/**
 * This extractor writes the generated messages to an CSV file.
 * It generates a GOOSE-oriented dataset (one sample per GOOSE).
 */
public class CSVWritter {
    static BufferedWriter bw;
    

    public static void processDataset(PriorityQueue<EthernetFrame> stationBusMessages, ArrayList<EthernetFrame> processBusMessages) throws IOException {
        // Writing Header
        writeDefaultHeader();

        // Writing Messages
        Goose previousGoose = null;
//        for (EthernetFrame gooseFrame : stationBusMessages) {
//            Goose goose = (Goose) gooseFrame;

        // temporario
        int contador= 10; // REMOVER DEPOIS DOS TESTES
        Logger.getLogger("CSVWritter").info(stationBusMessages.size() + " mensagens na fila.");
        while (!stationBusMessages.isEmpty()) {
            Goose goose = (Goose) stationBusMessages.poll();
            if (previousGoose != null) { // skips the first message
                Sv sv = ProtocolCorrelation.getCorrespondingSVFrame(processBusMessages, goose);
                String svString = sv.asCsv();
                String cycleStrig = ProtocolCorrelation.getCorrespondingSVFrameCycle(processBusMessages, goose, 80).asCsv();
                String gooseString = goose.asCSVFull();

//                contador = contador - 1; // REMOVER DEPOIS DOS TESTES
//                if(contador==0){ // REMOVER DEPOIS DOS TESTES
//                break;// REMOVER DEPOIS DOS TESTES
//                } // REMOVER DEPOIS DOS TESTES
                String gooseConsistency = IntermessageCorrelation.getConsistencyFeaturesAsCSV(goose, previousGoose);

                double delay = goose.getTimestamp() - sv.getTime();
                double e2eLatency = goose.getE2ELatencyMs();
                double receivedTimestamp = goose.getSubscriberRxTs() != null ? goose.getSubscriberRxTs() : goose.getTimestamp();
                write(svString + "," + cycleStrig + "," + gooseString + "," + gooseConsistency + "," + delay + "," + e2eLatency + "," + receivedTimestamp + "," + goose.getLabel());
            }
            previousGoose = goose.copy();
        }


    }

    private static void write(String line) throws IOException {
        bw.write(line);
        bw.newLine();
    }

    public static void startWriting(String filename) throws IOException {
        File fout = new File(filename);
        if (!fout.exists()) {
            fout.getParentFile().mkdirs();
            System.out.println("Directory created at: " + filename);
        }
        // overwrite by default (no append)
        FileOutputStream fos = new FileOutputStream(fout, true);
        bw = new BufferedWriter(new OutputStreamWriter(fos));
    }

    public static void writeLine(String line) throws IOException {
        write(line);
    }

    public static void writeDefaultHeader() throws IOException {
        String header = String.join(",", new String[] {
                "Time", "isbA", "isbB", "isbC", "vsbA", "vsbB", "vsbC",
                "isbARmsValue", "isbBRmsValue", "isbCRmsValue", "vsbARmsValue", "vsbBRmsValue", "vsbCRmsValue",
                "isbATrapAreaSum", "isbBTrapAreaSum", "isbCTrapAreaSum", "vsbATrapAreaSum", "vsbBTrapAreaSum", "vsbCTrapAreaSum",
                "t", "GooseTimestamp", "SqNum", "StNum", "cbStatus", "frameLen", "ethDst", "ethSrc", "ethType",
                "gooseTimeAllowedtoLive", "gooseAppid", "gooseLen", "TPID", "gocbRef", "datSet", "goID", "test", "confRev", "ndsCom",
                "numDatSetEntries", "APDUSize", "protocol", "stDiff", "sqDiff", "gooseLengthDiff", "cbStatusDiff",
                "apduSizeDiff", "frameLengthDiff", "timestampDiff", "tDiff", "timeFromLastChange", "delay", "e2eLatency", "receivedTimestamp", "class"
        });
        write(header);
    }

    public static void finishWriting() throws IOException {
        bw.close();
    }

}
