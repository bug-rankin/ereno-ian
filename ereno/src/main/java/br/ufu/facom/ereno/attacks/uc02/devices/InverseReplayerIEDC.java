package br.ufu.facom.ereno.attacks.uc02.devices;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.attacks.uc02.creator.InverseReplayCreatorC;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;

import java.util.logging.Logger;

/** Configurable variant of InverseReplayerIED (uc02C) */
public class InverseReplayerIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public InverseReplayerIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(GSVDatasetWriter.label[2]);
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfReplayMessages) {
        Logger.getLogger("InverseReplayerIEDC").info(
                "Feeding inverse replayer IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        InverseReplayCreatorC creator = new InverseReplayCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfReplayMessages);
    }
}
