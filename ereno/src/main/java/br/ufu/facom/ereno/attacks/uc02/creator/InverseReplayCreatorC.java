package br.ufu.facom.ereno.attacks.uc02.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/** Configurable creator for inverse replay (uc02) */
public class InverseReplayCreatorC implements MessageCreator {

    private float timeTakenByAttacker = 1;
    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public InverseReplayCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numReplayInstances) {
        for (int replayMessageIndex = 0; replayMessageIndex <= numReplayInstances; replayMessageIndex++) {

            // Pick one old GOOSE randomly
            int randomIndex = randomBetween(0, legitimateMessages.size() - 1);
            Goose replayMessage = legitimateMessages.get(randomIndex).copy();
            replayMessage.setLabel(GSVDatasetWriter.label[2]);  // label it as inverse replay (uc02)

            // Wait until the status changes
            boolean stop = false;
            for (int nextLegitimateIndex = randomIndex + 1; nextLegitimateIndex < legitimateMessages.size(); nextLegitimateIndex++) {
                if (replayMessage.getCbStatus() != legitimateMessages.get(nextLegitimateIndex).copy().getCbStatus()) {
                    Goose nextLegitimateGoose = legitimateMessages.get(nextLegitimateIndex).copy();

                    // Randomize the time taken by an attacker using configured delayMs range
                    double minMs = config.getDelayMinMs(300);  // Default 300ms
                    double maxMs = config.getDelayMaxMs(5000); // Default 5000ms (5 seconds)
                    
                    timeTakenByAttacker = (float) (randomBetween((int)minMs, (int)maxMs) / 1000.0);
                    replayMessage.setTimestamp(nextLegitimateGoose.getTimestamp() + timeTakenByAttacker);
                    ProtectionIED iedConverted = (ProtectionIED) ied;
                    if (iedConverted.getNumberOfMessages() < numReplayInstances) {
                        ied.addMessage(replayMessage.copy());
                    } else {
                        stop = true;
                        break;
                    }
                }
            }
            if (stop) {
                break;
            }
        }
    }
}
