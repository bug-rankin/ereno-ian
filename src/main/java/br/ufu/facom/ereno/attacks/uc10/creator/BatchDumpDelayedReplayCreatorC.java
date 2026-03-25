package br.ufu.facom.ereno.attacks.uc10.creator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

/**
 * UC10 variant: batch dump delayed replay.
 * Selected messages are delayed, then released in a tight micro-gap burst after the last selected message's delay window.
 */
public class BatchDumpDelayedReplayCreatorC implements MessageCreator {

    private final ArrayList<Goose> messageStream;
    private final AttackConfig config;

    public BatchDumpDelayedReplayCreatorC(ArrayList<Goose> messages, AttackConfig config) {
        this.messageStream = messages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numDelayInstances) {
        boolean shiftSendTimestamp = config.getBoolean("shiftSendTimestamp", true);
        double microGapMs = config.getDouble("microGapMs", 1.0);
        double microGapSeconds = Math.max(0.0, microGapMs) / 1000.0;
        boolean replaceWithFake = config.getBoolean("replaceWithFake", false);

        DelayedReplaySelector.SelectionResult selection = DelayedReplaySelector.selectDelayedCopies(
                messageStream, config, numDelayInstances, shiftSendTimestamp, replaceWithFake);
        List<Goose> delayedMessages = selection.getDelayedMessages();
        if (delayedMessages.isEmpty()) {
            return;
        }

        // Anchor at the latest delayed arrival, then release with micro-gaps.
        double anchorTime = delayedMessages.stream()
                .mapToDouble(this::arrivalTs)
                .max()
                .orElse(0.0);

        for (int i = 0; i < delayedMessages.size(); i++) {
            Goose g = delayedMessages.get(i);
            double scheduledTime = anchorTime + (microGapSeconds * i);
            retime(g, scheduledTime, shiftSendTimestamp);
        }

        ArrayList<Goose> combined = new ArrayList<>(delayedMessages.size() + selection.getFakeReplacements().size());
        combined.addAll(delayedMessages);
        for (DelayedReplaySelector.FakeReplacement fr : selection.getFakeReplacements()) {
            combined.add(fr.fake);
        }

        combined.sort(Comparator.comparingDouble(this::arrivalTs));
        combined.forEach(ied::addMessage);
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
