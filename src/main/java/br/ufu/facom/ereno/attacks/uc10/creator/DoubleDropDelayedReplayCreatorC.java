package br.ufu.facom.ereno.attacks.uc10.creator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

/**
 * UC10 variant: double drop delayed replay.
 * Selected delayed messages replace the earliest available slots after the delay window, dropping the originals.
 */
public class DoubleDropDelayedReplayCreatorC implements MessageCreator {

    private final ArrayList<Goose> messageStream;
    private final AttackConfig config;

    public DoubleDropDelayedReplayCreatorC(ArrayList<Goose> messages, AttackConfig config) {
        this.messageStream = messages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numDelayInstances) {
        boolean shiftSendTimestamp = config.getBoolean("shiftSendTimestamp", true);
        boolean replaceWithFake = config.getBoolean("replaceWithFake", false);

        DelayedReplaySelector.SelectionResult selection = DelayedReplaySelector.selectDelayedCopies(
                messageStream, config, numDelayInstances, shiftSendTimestamp, replaceWithFake);
        List<Goose> delayedMessages = selection.getDelayedMessages();
        if (delayedMessages.isEmpty()) {
            return;
        }

        delayedMessages.sort(Comparator.comparingDouble(this::arrivalTs));
        double attackStart = delayedMessages.stream().mapToDouble(this::arrivalTs).min().orElse(0.0);

        ArrayList<Goose> output = new ArrayList<>(messageStream.size());
        for (Goose g : messageStream) {
            output.add(g.copy());
        }

        if (replaceWithFake) {
            for (DelayedReplaySelector.FakeReplacement fr : selection.getFakeReplacements()) {
                if (fr.index >= 0 && fr.index < output.size()) {
                    output.set(fr.index, fr.fake);
                }
            }
        }

        int replaceIndex = findFirstIndexAtOrAfter(output, attackStart);
        for (Goose delayed : delayedMessages) {
            if (replaceIndex >= output.size()) {
                break;
            }
            // Ensure timing is consistent with the delayed arrival
            retime(delayed, arrivalTs(delayed), shiftSendTimestamp);
            output.set(replaceIndex, delayed);
            replaceIndex++;
        }

        for (Goose g : output) {
            ied.addMessage(g);
        }
    }

    private int findFirstIndexAtOrAfter(List<Goose> messages, double ts) {
        for (int i = 0; i < messages.size(); i++) {
            double arrival = arrivalTs(messages.get(i));
            if (arrival >= ts) {
                return i;
            }
        }
        return messages.size();
    }

    private double arrivalTs(Goose goose) {
        return goose.getSubscriberRxTs() != null ? goose.getSubscriberRxTs() : goose.getTimestamp();
    }

    private void retime(Goose goose, double newTimestamp, boolean shiftSendTimestamp) {
        if (shiftSendTimestamp) {
            goose.setTimestamp(newTimestamp);
            goose.setPublisherTxTs(newTimestamp);
        }
        goose.setSubscriberRxTs(newTimestamp);
    }
}
