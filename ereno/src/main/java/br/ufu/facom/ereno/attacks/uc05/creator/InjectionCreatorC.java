package br.ufu.facom.ereno.attacks.uc05.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/** Configurable injection creator (uc05) */
public class InjectionCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public InjectionCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        for (int i = 0; i <= numInstances; i++) {
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(GSVDatasetWriter.label[5]); // random injection label

            // Simply inject the message - injection attacks typically don't modify stNum
            // (that's what high_stnum_injection does)
            ProtectionIED injector = (ProtectionIED) ied;
            injector.addMessage(g.copy());
        }
    }
}
