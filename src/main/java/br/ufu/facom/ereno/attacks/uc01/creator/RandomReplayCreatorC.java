package br.ufu.facom.ereno.attacks.uc01.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;

import java.util.ArrayList;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/**
 * Configurable variant of RandomReplayCreator that reads parameters from attack config file
 */
public class RandomReplayCreatorC implements MessageCreator {
    ArrayList<Goose> legitimateMessages;
    private float timeTakenByAttacker = 1;
    private final AttackConfig config;

    public RandomReplayCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numReplayInstances) {

        for (int i = 0; i < numReplayInstances; i++) {
            // Pickups one old GOOSE randomly
            int randomIndex = randomBetween(0, legitimateMessages.size() - 1);
            Goose randomGoose = legitimateMessages.get(randomIndex).copy();
            Logger.getLogger("RandomReplayCreatorC").info("Captured the legitimate message at " + randomGoose.getTimestamp());
            randomGoose.setLabel(GSVDatasetWriter.label[1]);  // label it as random replay (uc01)

            // Get delay parameters from config (values in config are in seconds if < 10, else milliseconds)
            double delayMinMs = config.getDelayMinMs(10);    // Default 10ms
            double delayMaxMs = config.getDelayMaxMs(1000);  // Default 1000ms (1 second)
            
            // Get burst probability from config
            double burstProb = config.getNestedProb("burst", "prob", 0.35); // Default 35%
            
            // Decide whether to use burst (longer delay) based on burst probability
            boolean useBurst = (randomBetween(0, 100) / 100.0) < burstProb;
            
            if (useBurst) {
                // Use longer delays for burst
                double burstDelayMin = config.getNestedRangeMin("burst", "gapMs", 0.3) * 1000; // Convert to ms
                double burstDelayMax = config.getNestedRangeMax("burst", "gapMs", 2.0) * 1000;
                timeTakenByAttacker = (float) (randomBetween((int)burstDelayMin, (int)burstDelayMax) / 1000.0);
            } else {
                // Use normal delays
                timeTakenByAttacker = (float) (randomBetween((int)delayMinMs, (int)delayMaxMs) / 1000.0);
            }

            // Apply the time offset and send
            randomGoose.setTimestamp(randomGoose.getTimestamp() + timeTakenByAttacker);
            Logger.getLogger("RandomReplayCreatorC").info("Sent the replay message at " + randomGoose.getTimestamp() + "(time taken by attaker: " + timeTakenByAttacker + ")");

            // Send back the random replayed message to ReplayerIED
            ied.addMessage(randomGoose.copy());
        }

    }
}
