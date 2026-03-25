package br.ufu.facom.ereno.attacks.uc10.devices;

import java.util.logging.Logger;

import br.ufu.facom.ereno.attacks.uc10.creator.DoubleDropDelayedReplayCreatorC;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;

public class DoubleDropDelayedReplayIEDC extends ProtectionIED {

    private final ProtectionIED legitimateIED;
    private final AttackConfig attackConfig;

    public DoubleDropDelayedReplayIEDC(ProtectionIED legitimate, AttackConfig attackConfig) {
        super(GSVDatasetWriter.label[9]);
        this.legitimateIED = legitimate;
        this.attackConfig = attackConfig;
    }

    @Override
    public void run(int numOfDelayInstances) {
        Logger.getLogger("DoubleDropDelayedReplayIEDC").info(
                "Feeding double drop delayed replayer with " + legitimateIED.copyMessages().size() + " legitimate messages");
        DoubleDropDelayedReplayCreatorC creator = new DoubleDropDelayedReplayCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numOfDelayInstances);
    }
}
