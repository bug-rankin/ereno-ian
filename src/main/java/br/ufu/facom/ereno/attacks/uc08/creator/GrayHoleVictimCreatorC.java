package br.ufu.facom.ereno.attacks.uc08.creator;

import java.util.ArrayList;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;

public class GrayHoleVictimCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public GrayHoleVictimCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int targetMessageCount) {
        // Get configuration parameters
        double dropRateMin = config.getRangeMin("dropRate", 0.25);
        double dropRateMax = config.getRangeMax("dropRate", 0.35);
        double burstDropProb = config.getDouble("burstDropProb", 0.20);
        int burstDropMin = config.getNestedRangeMinInt("burstDropLen", "min", 3);
        int burstDropMax = config.getNestedRangeMaxInt("burstDropLen", "max", 7);
        boolean protectStatusChanges = config.getBoolean("protectStatusChanges", false);
        double statusChangeDropProb = config.getDouble("statusChangeDropProb", 0.25);
        
        // Calculate base drop rate
        double baseDropRate = dropRateMin + (Math.random() * (dropRateMax - dropRateMin));
        
        int messagesAdded = 0;
        int messagesDropped = 0;
        
        // Work with messages in temporal order to maintain realism
        boolean inBurstDrop = false;
        int burstDropRemaining = 0;
        boolean inDelayBurst = false;
        int delayBurstRemaining = 0;
        
        for (int i = 0; i < legitimateMessages.size() && messagesAdded < targetMessageCount; i++) {
            Goose goose = legitimateMessages.get(i);
            
            // Check if this is a status change (critical message)
            boolean isStatusChange = false;
            if (protectStatusChanges && i > 0) {
                isStatusChange = (goose.getCbStatus() != legitimateMessages.get(i-1).getCbStatus());
            }
            
            // Start a burst drop?
            if (!inBurstDrop && Math.random() < burstDropProb) {
                inBurstDrop = true;
                burstDropRemaining = (int)(burstDropMin + Math.random() * (burstDropMax - burstDropMin + 1));
            }
            
            // Decide whether to drop
            boolean shouldDrop = false;
            
            if (inBurstDrop && burstDropRemaining > 0) {
                // In burst drop mode - higher drop chance
                shouldDrop = !isStatusChange || Math.random() < statusChangeDropProb;
                burstDropRemaining--;
                if (burstDropRemaining == 0) {
                    inBurstDrop = false;
                }
            } else {
                // Normal drop decision
                double dropChance = isStatusChange ? statusChangeDropProb : baseDropRate;
                shouldDrop = Math.random() < dropChance;
            }
            
            if (shouldDrop) {
                messagesDropped++;
                continue; // Message dropped
            }
            
            // Message survives - add without delay
            Goose survivedMessage = goose.copy();
            survivedMessage.setLabel(GSVDatasetWriter.label[8]);
            
            ProtectionIED gh = (ProtectionIED) ied;
            gh.addMessage(survivedMessage);
            messagesAdded++;
        }
    }
}
