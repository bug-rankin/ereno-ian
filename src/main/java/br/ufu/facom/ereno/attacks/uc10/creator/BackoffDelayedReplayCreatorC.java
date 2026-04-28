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
 * UC10 variant: backoff delayed replay.
 *
 * <p>Captured frames are held back from the on-time stream (R) or
 * replaced by synthetic fillers (F). Once the flush window opens,
 * delayed copies are released at an accelerated rate (rateMultiplier x
 * the median periodic interval), and any legitimate frames that would
 * have been emitted during the flush are queued and dispatched after
 * the burst at the same accelerated cadence.
 */
public class BackoffDelayedReplayCreatorC implements MessageCreator {

    private final ArrayList<Goose> messageStream;
    private final AttackConfig config;

    public BackoffDelayedReplayCreatorC(ArrayList<Goose> messages, AttackConfig config) {
        this.messageStream = messages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numDelayInstances) {
        boolean shiftSendTimestamp = config.getBoolean("shiftSendTimestamp", true);
        double rateMultiplier = config.getDouble("rateMultiplier", 2.0);
        double effectiveMultiplier = rateMultiplier <= 0 ? 1.0 : rateMultiplier;
        boolean replaceWithFake = config.getBoolean("replaceWithFake", false);

        DelayedReplaySelector.SelectionResult selection = DelayedReplaySelector.selectDelayedCopies(
                messageStream, config, numDelayInstances, shiftSendTimestamp, replaceWithFake);

        Set<Integer> capturedIndices = selection.getCapturedIndices();
        List<Goose> delayedMessages = selection.getDelayedMessages();
        List<DelayedReplaySelector.FakeReplacement> fakeReplacements = selection.getFakeReplacements();

        if (delayedMessages.isEmpty()) {
            // No attacks scheduled - emit the legit stream untouched.
            for (Goose g : messageStream) {
                ied.addMessage(g.copy());
            }
            return;
        }

        delayedMessages.sort(Comparator.comparingDouble(this::arrivalTs));
        double flushStart = delayedMessages.stream().mapToDouble(this::arrivalTs).min().orElse(0.0);

        // Build the on-time view excluding captured frames; in F mode, substitute
        // each captured index with the corresponding fake.
        HashMap<Integer, Goose> fakeByIndex = new HashMap<>();
        if (replaceWithFake) {
            for (DelayedReplaySelector.FakeReplacement fr : fakeReplacements) {
                fakeByIndex.put(fr.index, fr.fake);
            }
        }

        ArrayList<Goose> preFlush = new ArrayList<>();
        ArrayList<Goose> queuedLegit = new ArrayList<>();
        for (int i = 0; i < messageStream.size(); i++) {
            Goose source;
            if (capturedIndices.contains(i)) {
                if (!replaceWithFake) {
                    continue; // R mode: captured frame is dropped from on-time stream
                }
                source = fakeByIndex.get(i);
                if (source == null) {
                    continue;
                }
            } else {
                source = messageStream.get(i).copy();
            }
            double ts = arrivalTs(source);
            if (ts < flushStart) {
                preFlush.add(source);
            } else {
                queuedLegit.add(source);
            }
        }

        double baseInterval = computeMedianGapSeconds(messageStream);
        double flushInterval = effectiveMultiplier > 0 ? baseInterval / effectiveMultiplier : baseInterval;
        if (flushInterval <= 0.0) {
            flushInterval = 0.001; // minimal spacing safeguard
        }

        double currentTs = flushStart;

        // Emit pre-flush messages unchanged
        preFlush.forEach(ied::addMessage);

        // Delayed messages first, then queued legit, all at accelerated clip
        for (Goose delayed : delayedMessages) {
            retime(delayed, currentTs, shiftSendTimestamp);
            ied.addMessage(delayed);
            currentTs += flushInterval;
        }

        queuedLegit.sort(Comparator.comparingDouble(this::arrivalTs));
        for (Goose legit : queuedLegit) {
            retime(legit, currentTs, shiftSendTimestamp);
            ied.addMessage(legit);
            currentTs += flushInterval;
        }
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

    private double computeMedianGapSeconds(ArrayList<Goose> messages) {
        if (messages.size() < 2) {
            return 0.001;
        }
        ArrayList<Double> ts = new ArrayList<>(messages.size());
        for (Goose g : messages) {
            ts.add(arrivalTs(g));
        }
        ts.sort(Double::compareTo);
        ArrayList<Double> gaps = new ArrayList<>(ts.size() - 1);
        for (int i = 1; i < ts.size(); i++) {
            gaps.add(ts.get(i) - ts.get(i - 1));
        }
        gaps.sort(Double::compareTo);
        int mid = gaps.size() / 2;
        if (gaps.size() % 2 == 0) {
            return (gaps.get(mid - 1) + gaps.get(mid)) / 2.0;
        }
        return gaps.get(mid);
    }
}
