package br.ufu.facom.ereno.attacks.uc07.creator;

import java.util.ArrayList;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

public class HighRateStNumInjectionCreator implements MessageCreator {
    ArrayList<Goose> legitimateMessages;

    /**
     * @param legitimateMessages - previously generated legitimate messages
     */
    public HighRateStNumInjectionCreator(ArrayList<Goose> legitimateMessages) {
        this.legitimateMessages = legitimateMessages;
    }

    /**
     * Generates flooding attack with high-rate stNum increments in bursts
     */
    @Override
    public void generate(IED ied, int numberofMessages) {
        int messagesGenerated = 0;
        double minTime = Double.valueOf(ConfigLoader.setupIED.minTime);
        
        while (messagesGenerated < numberofMessages) {
            // Generate a burst of flooding messages
            int burstSize = randomBetween(15, 200);
            
            // Pick a random legitimate message as a base
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose baseMessage = legitimateMessages.get(idx).copy();
            
            // Generate burst
            for (int j = 0; j < burstSize && messagesGenerated < numberofMessages; j++) {
                Goose g = baseMessage.copy();
                g.setLabel(GSVDatasetWriter.label[7]);
                
                // Flooding: increment stNum on every packet
                g.setStNum(baseMessage.getStNum() + j);
                
                // Increment sqNum with stride
                int sqnumStride = randomBetween(1, 4);
                g.setSqNum(baseMessage.getSqNum() + (j * sqnumStride));
                
                // Apply small random delay between messages in burst (0.05s to 0.5s)
                double randomDelay = randomBetween((int)(minTime * 50), (int)(minTime * 500)) / 1000.0;
                g.setTimestamp(baseMessage.getTimestamp() + (j * randomDelay));
                
                // Set short TTL typical of flooding attacks
                g.setGooseTimeAllowedtoLive(20);
                
                ied.addMessage(g.copy());
                messagesGenerated++;
            }
        }
    }
}
