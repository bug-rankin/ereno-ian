package br.ufu.facom.ereno.attacks.uc06.creator;

import java.util.ArrayList;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

public class HighStNumInjectionCreator implements MessageCreator {
    private final ArrayList<Goose> legitimateMessages;

    public HighStNumInjectionCreator(ArrayList<Goose> legitimateMessages) {
        super();
        this.legitimateMessages = legitimateMessages;
    }

    @Override
    public void generate(IED ied, int numberofMessages) {
        for (int i = 0; i < numberofMessages; i++) {
            // Pick a random legitimate message to base the attack on
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose baseMessage = legitimateMessages.get(idx);
            
            double minGoose = legitimateMessages.get(0).getTimestamp();
            double maxGoose = legitimateMessages.get(legitimateMessages.size() - 1).getTimestamp();
            double timestamp = randomBetween(minGoose, maxGoose);
            
            // Copy timing characteristics from legitimate message
            double t = baseMessage.getT();
            
            // Use moderately elevated stNum (20-50) - higher than normal (0-1) but realistic
            int stNum = randomBetween(20, 50);
            
            // Copy sqNum from legitimate message or use small variation
            int sqNum = baseMessage.getSqNum() + randomBetween(0, 5);
            
            // Copy status from legitimate message
            int cbStatus = baseMessage.isCbStatus();
            int timeAllowedToLive = 11000;
            int confRev = 1;

            // Create injection message based on legitimate characteristics
            Goose injectionMessage = new Goose(cbStatus, stNum, sqNum, timestamp, t, GSVDatasetWriter.label[6]);
            injectionMessage.setSqNum(sqNum);
            injectionMessage.setStNum(stNum);
            injectionMessage.setCbStatus(cbStatus);
            injectionMessage.setConfRev(confRev);
            injectionMessage.setGooseTimeAllowedtoLive(timeAllowedToLive);

            // Send the generated message to InjectorIED
            ied.addMessage(injectionMessage);

        }

    }
}
