package br.ufu.facom.ereno.attacks.uc01.creator;

import java.util.ArrayList;
import java.util.logging.Logger;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

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
        
        // Track the previous message (start with last legitimate)
        Goose previousMessage = legitimateMessages.get(legitimateMessages.size() - 1);

        for (int i = 0; i < numReplayInstances; i++) {
            // Select from RECENT messages only (last 20% of history) to reduce temporal anomalies
            // Use windowS config parameter to determine how far back to look
            double windowMinS = config.getWindowMinS(2.0);  // Default 2 seconds
            double windowMaxS = config.getWindowMaxS(8.0);  // Default 8 seconds
            
            // Find messages within the time window (use legitimate timeline, not previous message)
            Goose lastLegitimate = legitimateMessages.get(legitimateMessages.size() - 1);
            double windowSeconds = randomBetween((int)(windowMinS * 1000), (int)(windowMaxS * 1000)) / 1000.0;
            double cutoffTime = lastLegitimate.getTimestamp() - windowSeconds;
            
            // Find start index for the window
            int startIndex = legitimateMessages.size() - 1;
            for (int j = legitimateMessages.size() - 1; j >= 0; j--) {
                if (legitimateMessages.get(j).getTimestamp() < cutoffTime) {
                    startIndex = j + 1;
                    break;
                }
            }
            startIndex = Math.max(0, startIndex);
            
            // Pick random message from the window
            int randomIndex = randomBetween(startIndex, legitimateMessages.size() - 1);
            Goose randomGoose = legitimateMessages.get(randomIndex).copy();
            
            Logger.getLogger("RandomReplayCreatorC").info("Captured the legitimate message at " + randomGoose.getTimestamp());
            randomGoose.setLabel(GSVDatasetWriter.label[1]);  // label it as random replay (uc01)

            // Adjust stNum intelligently based on whether status changed from previous
            // This makes stDiff feature look more natural
            int stNumAdjustment;
            if (randomGoose.getCbStatus() != previousMessage.getCbStatus()) {
                // Status changed - stNum should reset or increment by 1
                stNumAdjustment = randomBetween(0, 1);
            } else {
                // Same status - keep same stNum
                stNumAdjustment = 0;
            }
            randomGoose.setStNum(previousMessage.getStNum() + stNumAdjustment);
            
            // Adjust sqNum more naturally - should generally increment from previous
            int sqNumAdjustment = randomBetween(1, 3); // Usually increments by 1-3
            randomGoose.setSqNum(previousMessage.getSqNum() + sqNumAdjustment);

            // Calculate typical inter-message delay from recent history
            float typicalDelay = 0.1f; // Default 100ms
            if (legitimateMessages.size() >= 2) {
                int recentIdx = Math.max(0, legitimateMessages.size() - 10);
                double sumDelays = 0;
                int count = 0;
                for (int j = recentIdx + 1; j < legitimateMessages.size(); j++) {
                    sumDelays += legitimateMessages.get(j).getTimestamp() - legitimateMessages.get(j-1).getTimestamp();
                    count++;
                }
                if (count > 0) {
                    typicalDelay = (float)(sumDelays / count);
                }
            }
            
            // Add some variation around the typical delay (use config if available)
            double delayMinMs = config.getDelayMinMs(50);
            double delayMaxMs = config.getDelayMaxMs(500);
            
            // Use typical delay with variation, bounded by config
            float variation = typicalDelay * randomBetween(-30, 50) / 100.0f; // -30% to +50%
            float calculatedDelay = typicalDelay + variation;
            
            // Ensure within configured bounds
            timeTakenByAttacker = Math.max((float)(delayMinMs/1000.0), 
                                   Math.min((float)(delayMaxMs/1000.0), calculatedDelay));

            // Set timestamp relative to previous message to create natural flow
            // This creates more natural timestampDiff values
            randomGoose.setTimestamp(previousMessage.getTimestamp() + timeTakenByAttacker);
            
            // Update "t" (time of last status change) to maintain realistic timeFromLastChange
            // If status matches previous, keep same t; otherwise update it
            if (randomGoose.getCbStatus() == previousMessage.getCbStatus()) {
                randomGoose.setT(previousMessage.getT());
            } else {
                // Status changed, so update t to current timestamp
                randomGoose.setT(randomGoose.getTimestamp());
            }
            
            Logger.getLogger("RandomReplayCreatorC").info("Sent the replay message at " + randomGoose.getTimestamp() + "(time taken by attaker: " + timeTakenByAttacker + ")");

            // Send back the random replayed message to ReplayerIED
            ied.addMessage(randomGoose.copy());
            
            // Update previous message for next iteration
            previousMessage = randomGoose;
        }

    }
}