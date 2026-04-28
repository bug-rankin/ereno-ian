package br.ufu.facom.ereno.attacks.uc10.creator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

/**
 * UC10 variant: batch-dump delayed replay.
 *
 * <p>The legitimate stream is delivered on time with captured frames
 * removed (R) or replaced by fake covers (F). All delayed copies are
 * held until the latest captured frame's natural arrival, then released
 * back-to-back at a tight micro-gap interval. This produces the
 * characteristic "burst dump" arrival pattern after the attack window.
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

        Set<Integer> capturedIndices = selection.getCapturedIndices();
        List<Goose> delayedMessages = selection.getDelayedMessages();
        List<DelayedReplaySelector.FakeReplacement> fakeReplacements = selection.getFakeReplacements();

        // Build the on-time live window with captured frames removed or
        // replaced by their fake covers (in F mode).
        ArrayList<Goose> liveWindow = new ArrayList<>(messageStream.size());
        HashMap<Integer, Goose> fakeByIndex = new HashMap<>();
        if (replaceWithFake) {
            for (DelayedReplaySelector.FakeReplacement fr : fakeReplacements) {
                fakeByIndex.put(fr.index, fr.fake);
            }
        }
        for (int i = 0; i < messageStream.size(); i++) {
            if (capturedIndices.contains(i)) {
                if (replaceWithFake) {
                    Goose fake = fakeByIndex.get(i);
                    if (fake != null) {
                        liveWindow.add(fake);
                    }
                }
                // R mode: drop captured frame entirely
            } else {
                liveWindow.add(messageStream.get(i).copy());
            }
        }

        // Anchor the burst dump at the latest captured frame's natural arrival,
        // then release each delayed copy back-to-back at microGap intervals.
        double anchorTime = delayedMessages.stream()
                .mapToDouble(this::arrivalTs)
                .max()
                .orElse(0.0);

        delayedMessages.sort(Comparator.comparingDouble(this::arrivalTs));
        for (int i = 0; i < delayedMessages.size(); i++) {
            double scheduledTime = anchorTime + (microGapSeconds * i);
            retime(delayedMessages.get(i), scheduledTime, shiftSendTimestamp);
        }

        ArrayList<Goose> combined = new ArrayList<>(liveWindow.size() + delayedMessages.size());
        combined.addAll(liveWindow);
        combined.addAll(delayedMessages);
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
