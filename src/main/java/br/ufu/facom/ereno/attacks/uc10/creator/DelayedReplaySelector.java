package br.ufu.facom.ereno.attacks.uc10.creator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

/**
 * Helper to select and delay messages according to UC10 config knobs.
 *
 * <p>UC10 delayed-replay attacks operate on a captured legitimate stream:
 * a subset of fault frames is "captured" by the attacker, removed from
 * on-time delivery, and replayed later. With {@code replaceWithFake=true}
 * the void left by the captured frame is back-filled with a synthetic
 * GOOSE that is intended to be indistinguishable from what the network
 * would otherwise have observed.
 *
 * <p>This selector returns:
 * <ul>
 *   <li>{@link SelectionResult#getCapturedIndices()} - indices into
 *       {@code messageStream} whose original frames were captured by the
 *       attacker. Creators must remove these from the on-time stream.</li>
 *   <li>{@link SelectionResult#getDelayedMessages()} - replayed copies
 *       (label {@code delayed_replay}) carrying the original payload but
 *       with timestamps shifted forward by the attack delay.</li>
 *   <li>{@link SelectionResult#getFakeReplacements()} - when
 *       {@code replaceWithFake=true}, one fake-filler per captured index.
 *       The fake is a faithful copy of the captured frame (same
 *       cbStatus / stNum / sqNum / payload) with the captured frame's
 *       on-time arrival timestamp. It is labelled {@code delayed_replay}
 *       so binary-mode evaluation tags it as part of the attack.</li>
 * </ul>
 */
public final class DelayedReplaySelector {

    private DelayedReplaySelector() {
    }

    public static SelectionResult selectDelayedCopies(ArrayList<Goose> messageStream,
                                                      AttackConfig config,
                                                      int numDelayInstances,
                                                      boolean shiftSendTimestamp,
                                                      boolean replaceWithFake) {
        int minBurstInterval = config.getNestedInt("burstInterval", "min", 5);
        int maxBurstInterval = config.getNestedInt("burstInterval", "max", 25);
        int burstMax = config.getInt("burstMax", 6);
        if (burstMax < 1) burstMax = 1;
        double selectionProb = config.getNestedDouble("selectionProb", "value", 1.0);

        double minNetworkDelayMs = config.getRangeMin("networkDelayMs", 1.0);
        double maxNetworkDelayMs = config.getRangeMax("networkDelayMs", 31.0);
        if (minNetworkDelayMs <= 0 || maxNetworkDelayMs <= 0 || maxNetworkDelayMs < minNetworkDelayMs) {
            minNetworkDelayMs = 1.0;
            maxNetworkDelayMs = 31.0;
        }

        ArrayList<Goose> delayedMessages = new ArrayList<>();
        ArrayList<FakeReplacement> fakeReplacements = new ArrayList<>();
        HashSet<Integer> capturedIndices = new HashSet<>();

        // Walk the stream, treating each maximal run of consecutive cbStatus==1 messages
        // as one fault-burst. After a delayed fault, require `cooldownTarget` non-fault
        // (cbStatus==0) messages to elapse before another fault becomes eligible.
        int nonFaultSinceLastDelay = 0;
        int cooldownTarget = 0; // 0 = no cooldown active; eligible immediately
        int i = 0;
        while (numDelayInstances > 0 && i < messageStream.size()) {
            Goose msg = messageStream.get(i);
            if (msg.getCbStatus() != 1) {
                if (cooldownTarget > 0) nonFaultSinceLastDelay++;
                i++;
                continue;
            }

            // detect fault-run boundaries
            int runStart = i;
            int runEnd = i;
            while (runEnd < messageStream.size() && messageStream.get(runEnd).getCbStatus() == 1) {
                runEnd++;
            }
            i = runEnd;

            // honor non-fault cooldown
            if (cooldownTarget > 0 && nonFaultSinceLastDelay < cooldownTarget) {
                continue;
            }

            if (selectionProb < 1.0 && randomBetween(0.0, 1.0) > selectionProb) {
                continue; // fault skipped; cooldown state unchanged
            }

            int runLength = runEnd - runStart;
            int delayCount = Math.min(runLength, burstMax);
            delayCount = Math.min(delayCount, numDelayInstances);

            for (int k = 0; k < delayCount; k++) {
                int captureIdx = runStart + k;
                Goose candidate = messageStream.get(captureIdx);
                Goose delayed = applyDelay(candidate, minNetworkDelayMs, maxNetworkDelayMs, shiftSendTimestamp);
                delayedMessages.add(delayed);
                capturedIndices.add(captureIdx);
                if (replaceWithFake) {
                    Goose fake = createFakeMessage(candidate);
                    fakeReplacements.add(new FakeReplacement(captureIdx, fake, arrivalTs(candidate)));
                }
                numDelayInstances--;
            }

            // arm a fresh cooldown counted in non-fault messages
            cooldownTarget = randomBetween(minBurstInterval, maxBurstInterval);
            nonFaultSinceLastDelay = 0;
        }

        return new SelectionResult(delayedMessages, fakeReplacements, capturedIndices);
    }

    /**
     * Builds a synthetic GOOSE intended to back-fill the void left by a
     * captured frame. The fake mirrors the captured frame's payload (so
     * the on-time observation appears unchanged to a passive observer)
     * but is labelled {@code delayed_replay} as ground truth: the frame
     * is part of the attacker's cover traffic, not legitimate output.
     */
    private static Goose createFakeMessage(Goose captured) {
        Goose fake = captured.copy();
        // Preserve cbStatus, stNum, sqNum, payload - fake is a faithful
        // cover for the captured frame on the wire.
        // Timestamps: fake arrives at the captured frame's original
        // on-time slot, since the original is being held back.
        double ts = arrivalTs(captured);
        fake.setTimestamp(ts);
        fake.setPublisherTxTs(ts);
        fake.setSubscriberRxTs(ts);
        fake.setLabel(GSVDatasetWriter.label[9]); // "delayed_replay" - attack ground truth
        return fake;
    }

    private static Goose applyDelay(Goose originalMessage,
                                    double minDelayMs,
                                    double maxDelayMs,
                                    boolean shiftSendTimestamp) {
        double attackDelaySeconds = randomBetween(minDelayMs, maxDelayMs) / 1000.0;
        Goose delayedGoose = originalMessage.copy();
        double originalSendTs = delayedGoose.getTimestamp();
        double originalReceiveTs = delayedGoose.getSubscriberRxTs() != null ? delayedGoose.getSubscriberRxTs() : originalSendTs;

        if (shiftSendTimestamp) {
            double updatedSendTs = originalSendTs + attackDelaySeconds;
            delayedGoose.setTimestamp(updatedSendTs);
            delayedGoose.setPublisherTxTs(updatedSendTs);
        } else {
            delayedGoose.setPublisherTxTs(originalSendTs);
        }

        delayedGoose.setSubscriberRxTs(originalReceiveTs + attackDelaySeconds);
        delayedGoose.setLabel(GSVDatasetWriter.label[9]);
        return delayedGoose;
    }

    private static double arrivalTs(Goose goose) {
        return goose.getSubscriberRxTs() != null ? goose.getSubscriberRxTs() : goose.getTimestamp();
    }

    public static class FakeReplacement {
        public final int index;
        public final Goose fake;
        public final double originalTs;

        public FakeReplacement(int index, Goose fake, double originalTs) {
            this.index = index;
            this.fake = fake;
            this.originalTs = originalTs;
        }
    }

    public static class SelectionResult {
        private final List<Goose> delayedMessages;
        private final List<FakeReplacement> fakeReplacements;
        private final Set<Integer> capturedIndices;

        public SelectionResult(List<Goose> delayedMessages,
                               List<FakeReplacement> fakeReplacements,
                               Set<Integer> capturedIndices) {
            this.delayedMessages = delayedMessages;
            this.fakeReplacements = fakeReplacements;
            this.capturedIndices = capturedIndices;
        }

        public List<Goose> getDelayedMessages() {
            return delayedMessages;
        }

        public List<FakeReplacement> getFakeReplacements() {
            return fakeReplacements;
        }

        /** Indices in the original messageStream that were captured by the attacker. */
        public Set<Integer> getCapturedIndices() {
            return capturedIndices;
        }

        public List<Goose> getFakeMessages() {
            ArrayList<Goose> fakes = new ArrayList<>(fakeReplacements.size());
            for (FakeReplacement fr : fakeReplacements) {
                fakes.add(fr.fake);
            }
            return fakes;
        }
    }
}
