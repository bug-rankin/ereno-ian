package br.ufu.facom.ereno.attacks.uc08.devices;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.attacks.uc08.creator.GrayHoleVictimCreatorC;

import java.util.logging.Logger;

public class GrayHoleVictimIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public GrayHoleVictimIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(GSVDatasetWriter.label[8]);
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfMessages) {
        Logger.getLogger("GrayHoleVictimIEDC").info(
                "Feeding GrayHole victim IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        GrayHoleVictimCreatorC creator = new GrayHoleVictimCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfMessages);
    }
}
