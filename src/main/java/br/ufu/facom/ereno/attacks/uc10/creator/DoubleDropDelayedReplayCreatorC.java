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
 * UC10 variant: double-drop delayed replay.
 *
 * <p>Captured frames are removed from the on-time stream (R) or
 * back-filled with synthetic covers (F). The replayed copies are then
 * delivered after the attack window, but instead of arriving alongside
 * the legitimate stream they OVERWRITE the next legitimate slots,
 * causing those legit frames to be dropped as well - hence
 * "double drop". Each replay therefore costs the network two frames:
 * the captured one and the legit one it stomps on.
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

        Set<Integer> capturedIndices = selection.getCapturedIndices();
        List<Goose> delayedMessages = selection.getDelayedMessages();
        List<DelayedReplaySelector.FakeReplacement> fakeReplacements = selection.getFakeReplacements();

        if (delayedMessages.isEmpty()) {
            for (Goose g : messageStream) {
                ied.addMessage(g.copy());
            }
            return;
        }

        delayedMessages.sort(Comparator.comparingDouble(this::arrivalTs));
        double attackStart = delayedMessages.stream().mapToDouble(this::arrivalTs).min().orElse(0.0);

        // Build the live-window output: drop captured frames in R mode, swap in
        // fakes at the captured slots in F mode.
        HashMap<Integer, Goose> fakeByIndex = new HashMap<>();
        if (replaceWithFake) {
            for (DelayedReplaySelector.FakeReplacement fr : fakeReplacements) {
                fakeByIndex.put(fr.index, fr.fake);
            }
        }

        ArrayList<Goose> output = new ArrayList<>(messageStream.size());
        for (int i = 0; i < messageStream.size(); i++) {
            if (capturedIndices.contains(i)) {
                if (replaceWithFake) {
                    Goose fake = fakeByIndex.get(i);
                    if (fake != null) {
                        output.add(fake);
                    }
                }
                // R mode: gap left where captured frame would have been
            } else {
                output.add(messageStream.get(i).copy());
            }
        }

        // Stomp delayed copies on top of the next-available legit slots after
        // attackStart. Each replaced slot loses its legitimate frame -> the
        // signature "double drop".
        int replaceIndex = findFirstIndexAtOrAfter(output, attackStart);
        for (Goose delayed : delayedMessages) {
            if (replaceIndex >= output.size()) {
                break;
            }
            // Skip slots that are themselves fakes/replays so we only stomp on
            // genuinely legitimate slots when possible.
            while (replaceIndex < output.size() && isAttackArtifact(output.get(replaceIndex))) {
                replaceIndex++;
            }
            if (replaceIndex >= output.size()) {
                break;
            }
            retime(delayed, arrivalTs(delayed), shiftSendTimestamp);
            output.set(replaceIndex, delayed);
            replaceIndex++;
        }

        for (Goose g : output) {
            ied.addMessage(g);
        }
    }

    private boolean isAttackArtifact(Goose g) {
        String label = g.getLabel();
        return label != null && !label.equals(br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter.label[0]);
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
