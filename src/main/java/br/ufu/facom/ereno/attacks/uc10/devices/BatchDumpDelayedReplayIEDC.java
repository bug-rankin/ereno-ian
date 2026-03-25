package br.ufu.facom.ereno.attacks.uc10.devices;

import java.util.logging.Logger;

import br.ufu.facom.ereno.attacks.uc10.creator.BatchDumpDelayedReplayCreatorC;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.ProtectionIED;

public class BatchDumpDelayedReplayIEDC extends ProtectionIED {

    private final ProtectionIED legitimateIED;
    private final AttackConfig attackConfig;

    public BatchDumpDelayedReplayIEDC(ProtectionIED legitimate, AttackConfig attackConfig) {
        super(GSVDatasetWriter.label[9]);
        this.legitimateIED = legitimate;
        this.attackConfig = attackConfig;
    }

    @Override
    public void run(int numOfDelayInstances) {
        Logger.getLogger("BatchDumpDelayedReplayIEDC").info(
                "Feeding batch dump delayed replayer with " + legitimateIED.copyMessages().size() + " legitimate messages");
        BatchDumpDelayedReplayCreatorC creator = new BatchDumpDelayedReplayCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numOfDelayInstances);
    }
}
