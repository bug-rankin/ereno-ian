package br.ufu.facom.ereno.attacks.uc07.devices;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.attacks.uc07.creator.HighRateStNumInjectionCreatorC;

import java.util.logging.Logger;

public class HighRateStNumInjectorIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public HighRateStNumInjectorIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(GSVDatasetWriter.label[7]);
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfMessages) {
        Logger.getLogger("HighRateStNumInjectorIEDC").info(
                "Feeding HighRate injector IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        HighRateStNumInjectionCreatorC creator = new HighRateStNumInjectionCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfMessages);
    }
}
