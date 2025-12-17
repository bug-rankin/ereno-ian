package br.ufu.facom.ereno.attacks.uc10.devices;

import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.attacks.uc10.creator.DelayedReplayCreatorC;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;

import java.util.logging.Logger;

public class DelayedReplayIEDC extends ProtectionIED {

    ProtectionIED legitimateIED;
    AttackConfig attackConfig;

    public DelayedReplayIEDC(ProtectionIED legitimate, AttackConfig attackConfig) {
        super(GSVDatasetWriter.label[9]);
        this.legitimateIED = legitimate;
        this.attackConfig = attackConfig;
    }

    @Override
    public void run(int numOfDelayInstances) {
        Logger.getLogger("DelayedReplayIEDC").info(
        "Feeding delayed replayer IED with" + legitimateIED.copyMessages().size() + " legitimate messages");
        DelayedReplayCreatorC delayedReplayCreatorC = new DelayedReplayCreatorC(legitimateIED.copyMessages(), attackConfig);
        delayedReplayCreatorC.generate(this, numOfDelayInstances);
    }

}
