package br.ufu.facom.ereno.attacks.uc08.devices;

import br.ufu.facom.ereno.attacks.uc08.creator.GrayHoleVictimCreator;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;

import java.util.logging.Logger;

public class GrayHoleVictimIED extends ProtectionIED {  // Gray hole attackers does not have any knowledge about the victim, thus it extends a generic IED

    ProtectionIED legitimateIED; // GrayHoleVictimIED will discard mensagens from that legitimate device
    private double dropProbability = 0.3; // default 30% drop rate

    public GrayHoleVictimIED(ProtectionIED legitimate) {
        super(GSVDatasetWriter.label[8]);
        this.legitimateIED = legitimate;
    }
    
    public GrayHoleVictimIED(ProtectionIED legitimate, double dropProbability) {
        super(GSVDatasetWriter.label[8]);
        this.legitimateIED = legitimate;
        this.dropProbability = dropProbability;
    }

    @Override
    public void run(int targetMessageCount) {
        Logger.getLogger("GrayHoleVictimIED").info(
                "Feeding gray hole victim IED with " + legitimateIED.getMessages().size() + " legitimate messages");
        messageCreator = new GrayHoleVictimCreator(legitimateIED.copyMessages(), dropProbability); // feeds the message creator with legitimate messages
        messageCreator.generate(this, targetMessageCount); // pass itself to receive messages from generator
    }

}
