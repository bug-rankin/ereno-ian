package br.ufu.facom.ereno.attacks.uc06.devices;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.attacks.uc06.creator.HighStNumInjectionCreatorC;

import java.util.logging.Logger;

public class HighStNumInjectorIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public HighStNumInjectorIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(GSVDatasetWriter.label[6]);
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfMessages) {
        Logger.getLogger("HighStNumInjectorIEDC").info(
                "Feeding HighStNum injector IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        HighStNumInjectionCreatorC creator = new HighStNumInjectionCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfMessages);
    }
}
