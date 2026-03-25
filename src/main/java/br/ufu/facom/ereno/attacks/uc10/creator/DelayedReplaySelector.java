package br.ufu.facom.ereno.attacks.uc10.creator;

import java.util.ArrayList;
import java.util.List;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

/**
 * Helper to select and delay messages according to UC10 config knobs.
 */
public final class DelayedReplaySelector {

    private DelayedReplaySelector() {
    }

    public static SelectionResult selectDelayedCopies(ArrayList<Goose> messageStream,
                                                      AttackConfig config,
                                                      int numDelayInstances,
                                                      boolean shiftSendTimestamp,
                                                      boolean replaceWithFake) {
        int minInterval = config.getNestedInt("selectionInterval", "min", 5);
        int maxInterval = config.getNestedInt("selectionInterval", "max", 25);
        int minBurstInterval = config.getNestedInt("burstInterval", "min", 5);
        int maxBurstInterval = config.getNestedInt("burstInterval", "max", 25);
        int minBurstSize = config.getNestedInt("burstSize", "min", 5);
        int maxBurstSize = config.getNestedInt("burstSize", "max", 25);

        int selectionInterval = randomBetween(minInterval, maxInterval);
        int burstInterval = randomBetween(minBurstInterval, maxBurstInterval);
        int burstSize = randomBetween(minBurstSize, maxBurstSize);
        double selectionProb = config.getNestedDouble("selectionProb", "value", 0.5);
        boolean burstMode = config.getBoolean("burstMode", false);

        double minNetworkDelayMs = config.getRangeMin("networkDelayMs", 1.0);
        double maxNetworkDelayMs = config.getRangeMax("networkDelayMs", 31.0);
        if (minNetworkDelayMs <= 0 || maxNetworkDelayMs <= 0 || maxNetworkDelayMs < minNetworkDelayMs) {
            minNetworkDelayMs = 1.0;
            maxNetworkDelayMs = 31.0;
        }

        int burstMessageCounter = 0;
        int burstIntervalCounter = 0;
        int selectionIntervalCounter = 0;
        ArrayList<Goose> delayedMessages = new ArrayList<>();
        ArrayList<FakeReplacement> fakeReplacements = new ArrayList<>();

        for (int i = 0; numDelayInstances > 0 && i < messageStream.size(); i++) {
            Goose candidate = messageStream.get(i);
            if (candidate.getCbStatus() != 1) {
                continue;
            }

            if (burstMode) {
                if (burstIntervalCounter == burstInterval) {
                    burstMessageCounter = 0;
                    burstIntervalCounter = 0;
                } else if (burstMessageCounter == burstSize) {
                    burstIntervalCounter++;
                    continue;
                }

                Goose delayed = applyDelay(candidate, minNetworkDelayMs, maxNetworkDelayMs, shiftSendTimestamp);
                delayedMessages.add(delayed);
                if (replaceWithFake) {
                    Goose fake = createFakeMessage(messageStream, i, candidate);
                    fakeReplacements.add(new FakeReplacement(i, fake, arrivalTs(candidate)));
                }
                numDelayInstances--;
                burstMessageCounter++;
                continue;
            }

            // non-burst path
            if (selectionIntervalCounter == selectionInterval) {
                selectionIntervalCounter = 0;
            } else if (selectionIntervalCounter < selectionInterval && selectionIntervalCounter >= 1) {
                selectionIntervalCounter++;
                continue;
            }

            double selectionValue = randomBetween(0.0, 1.0);
            if (selectionValue > selectionProb) {
                continue;
            }

            Goose delayed = applyDelay(candidate, minNetworkDelayMs, maxNetworkDelayMs, shiftSendTimestamp);
            delayedMessages.add(delayed);
            if (replaceWithFake) {
                Goose fake = createFakeMessage(messageStream, i, candidate);
                fakeReplacements.add(new FakeReplacement(i, fake, arrivalTs(candidate)));
            }
            numDelayInstances--;
            selectionIntervalCounter++;
        }

        return new SelectionResult(delayedMessages, fakeReplacements);
    }

    private static Goose createFakeMessage(ArrayList<Goose> stream, int index, Goose candidate) {
        Goose base = index > 0 ? stream.get(index - 1) : candidate;
        Goose fake = base.copy();
        double ts = arrivalTs(candidate);
        fake.setCbStatus(0);
        fake.setLabel(GSVDatasetWriter.label[0]);
        fake.setTimestamp(ts);
        fake.setPublisherTxTs(ts);
        fake.setSubscriberRxTs(ts);
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

        public SelectionResult(List<Goose> delayedMessages, List<FakeReplacement> fakeReplacements) {
            this.delayedMessages = delayedMessages;
            this.fakeReplacements = fakeReplacements;
        }

        public List<Goose> getDelayedMessages() {
            return delayedMessages;
        }

        public List<FakeReplacement> getFakeReplacements() {
            return fakeReplacements;
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
