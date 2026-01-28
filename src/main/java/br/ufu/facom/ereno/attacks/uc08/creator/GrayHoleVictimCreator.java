/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.ufu.facom.ereno.attacks.uc08.creator;

import java.util.ArrayList;
import java.util.logging.Logger;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

/**
 * @author silvio
 */
public class GrayHoleVictimCreator implements MessageCreator {
    ArrayList<Goose> legitimateMessages;
    private double dropProbability = 0.3; // default 30% drop rate
    
    public GrayHoleVictimCreator(ArrayList<Goose> legitimateMessages) {
        this.legitimateMessages = legitimateMessages;
    }
    
    public GrayHoleVictimCreator(ArrayList<Goose> legitimateMessages, double dropProbability) {
        this.legitimateMessages = legitimateMessages;
        this.dropProbability = dropProbability;
    }

    @Override
    public void generate(IED ied, int targetMessageCount) {
        // Gray hole attack with higher drop rate for easier detection
        int messagesAdded = 0;
        int messagesDropped = 0;
        
        // Work with messages in temporal order
        boolean inBurstDrop = false;
        int burstDropRemaining = 0;
        
        for (int i = 0; i < legitimateMessages.size() && messagesAdded < targetMessageCount; i++) {
            Goose goose = legitimateMessages.get(i);
            
            // Start a burst drop?
            if (!inBurstDrop && randomBetween(0, 1000) < 200) { // 20% chance
                inBurstDrop = true;
                burstDropRemaining = randomBetween(3, 7);
            }
            
            // Decide whether to drop
            boolean shouldDrop = false;
            
            if (inBurstDrop && burstDropRemaining > 0) {
                // In burst drop mode - drop messages
                shouldDrop = true;
                burstDropRemaining--;
                if (burstDropRemaining == 0) {
                    inBurstDrop = false;
                }
            } else {
                // Normal drop decision with higher probability (25-35%)
                double dropChance = 0.25 + (randomBetween(0, 100) / 1000.0); // 25-35%
                shouldDrop = (randomBetween(0, 1000) / 1000.0) < dropChance;
            }
            
            if (shouldDrop) {
                messagesDropped++;
                continue; // Message dropped
            }
            
            // Message survives - add without delay
            Goose survivedMessage = goose.copy();
            survivedMessage.setLabel(GSVDatasetWriter.label[8]);
            
            ied.addMessage(survivedMessage);
            messagesAdded++;
        }
        
        Logger.getLogger("GrayHoleVictimCreator").info(
            String.format("Gray hole attack: %d messages passed, %d dropped (%.1f%% drop rate)",
                messagesAdded, messagesDropped, 
                messagesAdded > 0 ? (100.0 * messagesDropped / (messagesAdded + messagesDropped)) : 0));
    }
}
