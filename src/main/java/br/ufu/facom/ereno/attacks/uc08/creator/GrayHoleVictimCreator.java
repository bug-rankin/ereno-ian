/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.ufu.facom.ereno.attacks.uc08.creator;

import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.general.IED.randomBetween;

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
        // Gray hole attack: selectively drop messages to reach target count
        // We need to select messages that "survive" the gray hole
        
        int messagesAdded = 0;
        int messagesDropped = 0;
        
        // Shuffle to randomize which messages get dropped
        ArrayList<Goose> shuffledMessages = new ArrayList<>(legitimateMessages);
        Collections.shuffle(shuffledMessages);
        
        for (Goose goose : shuffledMessages) {
            if (messagesAdded >= targetMessageCount) {
                break; // We have enough messages
            }
            
            // Drop message based on probability
            if (randomBetween(0, 100) < (dropProbability * 100)) {
                messagesDropped++;
                continue; // This message is dropped by the gray hole
            }
            
            // Message passes through the gray hole
            Goose survivedMessage = goose.copy();
            survivedMessage.setLabel(GSVDatasetWriter.label[8]); // label it as gray hole attack (uc08)
            ied.addMessage(survivedMessage);
            messagesAdded++;
        }
        
        Logger.getLogger("GrayHoleVictimCreator").info(
            String.format("Gray hole attack: %d messages passed, %d dropped (%.1f%% drop rate)",
                messagesAdded, messagesDropped, dropProbability * 100));
    }
}
