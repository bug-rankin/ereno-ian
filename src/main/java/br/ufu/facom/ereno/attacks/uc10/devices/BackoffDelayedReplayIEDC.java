package br.ufu.facom.ereno.attacks.uc10.devices;

import java.util.logging.Logger;

import br.ufu.facom.ereno.attacks.uc10.creator.BackoffDelayedReplayCreatorC;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;

public class BackoffDelayedReplayIEDC extends ProtectionIED {

    private final ProtectionIED legitimateIED;
    private final AttackConfig attackConfig;

    public BackoffDelayedReplayIEDC(ProtectionIED legitimate, AttackConfig attackConfig) {
        super(GSVDatasetWriter.label[9]);
        this.legitimateIED = legitimate;
        this.attackConfig = attackConfig;
    }

    @Override
    public void run(int numOfDelayInstances) {
        Logger.getLogger("BackoffDelayedReplayIEDC").info(
                "Feeding backoff delayed replayer with " + legitimateIED.copyMessages().size() + " legitimate messages");
        BackoffDelayedReplayCreatorC creator = new BackoffDelayedReplayCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numOfDelayInstances);
    }
}
