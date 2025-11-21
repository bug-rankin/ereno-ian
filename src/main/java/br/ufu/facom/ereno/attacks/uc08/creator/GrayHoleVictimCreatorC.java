package br.ufu.facom.ereno.attacks.uc08.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;
import java.util.Collections;

import static br.ufu.facom.ereno.general.IED.randomBetween;

public class GrayHoleVictimCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public GrayHoleVictimCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int targetMessageCount) {
        // Get drop probability from config (0.0 to 1.0)
        double dropProbability = config.getProbability("dropProbability", 0.3); // Default 30%
        
        // Shuffle messages to randomize selection
        ArrayList<Goose> shuffled = new ArrayList<>(legitimateMessages);
        Collections.shuffle(shuffled);
        
        int messagesAdded = 0;
        int index = 0;
        
        // Keep adding messages until we reach target count
        while (messagesAdded < targetMessageCount && index < shuffled.size()) {
            Goose g = shuffled.get(index).copy();
            g.setLabel(GSVDatasetWriter.label[8]);
            
            // Randomly decide whether to drop this message
            double rand = Math.random();
            if (rand >= dropProbability) {
                // Don't drop - add the message
                ProtectionIED gh = (ProtectionIED) ied;
                gh.addMessage(g);
                messagesAdded++;
            }
            // else: message is dropped (simulating grayhole behavior)
            
            index++;
            
            // If we've gone through all messages but haven't reached target, start over
            if (index >= shuffled.size() && messagesAdded < targetMessageCount) {
                index = 0;
                Collections.shuffle(shuffled); // Re-shuffle for variety
            }
        }
    }
}
