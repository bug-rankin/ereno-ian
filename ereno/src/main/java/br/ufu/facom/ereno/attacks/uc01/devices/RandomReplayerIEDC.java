package br.ufu.facom.ereno.attacks.uc01.devices;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.attacks.uc01.creator.RandomReplayCreatorC;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;

import java.util.logging.Logger;

public class RandomReplayerIEDC extends ProtectionIED {

    ProtectionIED legitimateIED; // ReplayerIED will replay mensagens from that legitimate device
    AttackConfig attackConfig;

    public RandomReplayerIEDC(ProtectionIED legitimate, AttackConfig config) {
        super(GSVDatasetWriter.label[1]);
        this.legitimateIED = legitimate;
        this.attackConfig = config;
    }

    @Override
    public void run(int numberOfReplayMessages) {
        Logger.getLogger("ReplayerIEDC").info(
                "Feeding replayer IEDC with " + legitimateIED.copyMessages().size() + " legitimate messages");
        RandomReplayCreatorC creator = new RandomReplayCreatorC(legitimateIED.copyMessages(), attackConfig);
        creator.generate(this, numberOfReplayMessages);
    }

}
