package br.ufu.facom.ereno.attacks.uc05.devices;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.attacks.uc05.creator.InjectionCreatorC;

import java.util.logging.Logger;

public class InjectorIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public InjectorIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(GSVDatasetWriter.label[5]);
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfMessages) {
        Logger.getLogger("InjectorIEDC").info(
                "Feeding injector IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        InjectionCreatorC creator = new InjectionCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfMessages);
    }
}
