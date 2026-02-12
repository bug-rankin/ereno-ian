package br.ufu.facom.ereno.attacks.uc10.devices;

import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.attacks.uc10.creator.DelayedReplayCreator;
import br.ufu.facom.ereno.general.ProtectionIED;

import java.util.ArrayList;
import java.util.logging.Logger;

public class DelayedReplayIED extends ProtectionIED {

    ProtectionIED legitimateIED;

    public DelayedReplayIED(ProtectionIED legitimate) {
        super(GSVDatasetWriter.label[9]); // must add label
        this.legitimateIED = legitimate;
    }

    @Override
    public void run(int numOfDelayInstances) {
        Logger.getLogger("DelayedReplayIED").info(
                "Feeding delayed replayer IED with " + legitimateIED.copyMessages().size() + " legitimate messages");
        messageCreator = new DelayedReplayCreator(new ArrayList<>(legitimateIED.copyMessages())); // feeds the message creator with legitimate messages
        messageCreator.generate(this, numOfDelayInstances); // pass itself to receive messages from generator
    }

}
