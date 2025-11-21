package br.ufu.facom.ereno.attacks.uc03.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/** Configurable masquerade fake fault creator (uc03) */
public class MasqueradeFakeFaultCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public MasqueradeFakeFaultCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        for (int i = 0; i <= numInstances; i++) {
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(GSVDatasetWriter.label[3]); // masquerade outage

            // Get fault parameters from config
            double faultProbability = config.getNestedProb("fault", "prob", 0.6); // Default 60%
            int delayMin = config.getNestedRangeMinInt("fault", "durationMs", 50);  // Default 50ms
            int delayMax = config.getNestedRangeMaxInt("fault", "durationMs", 800); // Default 800ms

            // Decide if we fake a fault based on probability
            if (Math.random() < faultProbability) {
                // emulate network delay
                int delayMs = randomBetween(delayMin, delayMax);
                g.setTimestamp(g.getTimestamp() + (delayMs / 1000.0));
                ProtectionIED mf = (ProtectionIED) ied;
                mf.addMessage(g.copy());
            }
        }
    }
}
