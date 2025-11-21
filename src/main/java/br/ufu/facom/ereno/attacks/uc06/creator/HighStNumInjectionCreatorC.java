package br.ufu.facom.ereno.attacks.uc06.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;

import static br.ufu.facom.ereno.general.IED.randomBetween;

public class HighStNumInjectionCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public HighStNumInjectionCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        for (int i = 0; i <= numInstances; i++) {
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(GSVDatasetWriter.label[6]);

            // Get stNum jump range from config
            int stMin = config.getRangeMinInt("jump", 5);    // Default min jump 5
            int stMax = config.getRangeMaxInt("jump", 200);  // Default max jump 200
            
            int stNum = randomBetween(stMin, stMax);
            g.setStNum(stNum);

            ProtectionIED h = (ProtectionIED) ied;
            h.addMessage(g.copy());
        }
    }
}
