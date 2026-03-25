package br.ufu.facom.ereno.attacks.uc10.creator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

/**
 * UC10 variant: backoff delayed replay.
 * Delayed messages are flushed faster than normal (configurable multiplier);
 * legitimate messages that would have been emitted during the flush are queued and sent in the accelerated stream.
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
        List<Goose> delayedMessages = selection.getDelayedMessages();
        if (delayedMessages.isEmpty()) {
            return;
        }

        delayedMessages.sort(Comparator.comparingDouble(this::arrivalTs));
        double flushStart = delayedMessages.stream().mapToDouble(this::arrivalTs).min().orElse(0.0);

        ArrayList<Goose> preFlush = new ArrayList<>();
        ArrayList<Goose> queuedLegit = new ArrayList<>();

        for (Goose message : messageStream) {
            double ts = arrivalTs(message);
            if (ts < flushStart) {
                preFlush.add(message.copy());
            } else {
                queuedLegit.add(message.copy());
            }
        }

        // classify fake replacements alongside legit
        for (DelayedReplaySelector.FakeReplacement fr : selection.getFakeReplacements()) {
            double ts = arrivalTs(fr.fake);
            if (ts < flushStart) {
                preFlush.add(fr.fake);
            } else {
                queuedLegit.add(fr.fake);
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

        // keep queued messages in arrival order before accelerated flush
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
