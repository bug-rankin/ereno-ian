package br.ufu.facom.ereno.attacks.uc03.devices;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.attacks.uc03.creator.MasqueradeFakeFaultCreatorC;

import java.util.logging.Logger;

/** Configurable variant of MasqueradeFakeFaultIED (uc03C) */
public class MasqueradeFakeFaultIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public MasqueradeFakeFaultIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(GSVDatasetWriter.label[3]);
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfMessages) {
        Logger.getLogger("MasqueradeFakeFaultIEDC").info(
                "Feeding masquerade-fault IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        MasqueradeFakeFaultCreatorC creator = new MasqueradeFakeFaultCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfMessages);
    }
}
