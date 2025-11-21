package br.ufu.facom.ereno.attacks.uc07.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;

import static br.ufu.facom.ereno.general.IED.randomBetween;

public class HighRateStNumInjectionCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public HighRateStNumInjectionCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        for (int i = 0; i <= numInstances; i++) {
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(GSVDatasetWriter.label[7]);

            // Get gap timing from config (in milliseconds)
            double gapMinMs = config.getRangeMin("gapMs", 0.05) * 1000; // Default 0.05s = 50ms
            double gapMaxMs = config.getRangeMax("gapMs", 0.5) * 1000;   // Default 0.5s = 500ms
            
            // Apply random gap
            double gapSeconds = randomBetween((int)gapMinMs, (int)gapMaxMs) / 1000.0;
            g.setTimestamp(g.getTimestamp() + gapSeconds);

            ProtectionIED h = (ProtectionIED) ied;
            h.addMessage(g.copy());
        }
    }
}
