package br.ufu.facom.ereno.attacks.uc07.creator;

import java.util.ArrayList;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;

public class HighRateStNumInjectionCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public HighRateStNumInjectionCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        // Get burst size range from config
        int burstMin = config.getRangeMinInt("burst", 15);
        int burstMax = config.getRangeMaxInt("burst", 200);
        
        // Get gap timing from config (in seconds)
        double gapMinMs = config.getRangeMin("gapMs", 0.05);   // Default 0.05s
        double gapMaxMs = config.getRangeMax("gapMs", 0.5);    // Default 0.5s
        
        // Check if we should increment stNum on every packet (flooding behavior)
        boolean stnumEveryPacket = config.getBoolean("stnumEveryPacket", true);
        
        // Get sqNum stride values
        int[] sqnumStrides = config.getIntArray("sqnumStrideValues", new int[]{1, 2, 4});
        
        // Get TTL 
        int ttlMs = config.getInt("ttlMs", 20);
        
        ProtectionIED h = (ProtectionIED) ied;
        
        int messagesGenerated = 0;
        while (messagesGenerated < numInstances) {
            // Generate a burst of messages
            int burstSize = randomBetween(burstMin, burstMax);
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose baseMessage = legitimateMessages.get(idx).copy();
            
            // Random sqNum stride for this burst
            int sqnumStride = sqnumStrides[randomBetween(0, sqnumStrides.length - 1)];
            
            for (int j = 0; j < burstSize && messagesGenerated < numInstances; j++) {
                Goose g = baseMessage.copy();
                g.setLabel(GSVDatasetWriter.label[7]);
                
                // Flooding: increment stNum on every packet
                if (stnumEveryPacket) {
                    g.setStNum(baseMessage.getStNum() + j);
                }
                
                // Increment sqNum with stride
                g.setSqNum(baseMessage.getSqNum() + (j * sqnumStride));
                
                // Apply small gap between messages in burst
                double gapSeconds = randomBetween((int)(gapMinMs * 1000), (int)(gapMaxMs * 1000)) / 1000.0;
                g.setTimestamp(baseMessage.getTimestamp() + (j * gapSeconds));
                
                // Set TTL
                g.setGooseTimeAllowedtoLive(ttlMs);
                
                h.addMessage(g.copy());
                messagesGenerated++;
            }
        }
    }
}
