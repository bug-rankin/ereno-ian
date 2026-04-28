package br.ufu.facom.ereno.attacks.uc10.creator;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

/**
 * UC10 baseline delayed-replay creator.
 *
 * <p>Emits a faithful view of the network during the attack window:
 * the legitimate stream is passed through with captured frames removed
 * (R mode) or replaced with synthetic fillers (F mode), and delayed
 * copies of the captured frames are appended at their shifted arrival
 * times. The resulting segment therefore contains both legitimate
 * traffic (label {@code normal}) and attack artifacts (label
 * {@code delayed_replay}), giving downstream binary classification a
 * realistic mixed-class window to evaluate against.
 */
public class DelayedReplayCreatorC implements MessageCreator {

    private final ArrayList<Goose> messageStream;
    private final AttackConfig config;

    public DelayedReplayCreatorC(ArrayList<Goose> messages, AttackConfig config) {
        this.messageStream = messages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numDelayInstances) {
        boolean shiftSendTimestamp = config.getBoolean("shiftSendTimestamp", true);
        String orderBy = config.getString("orderBy", "received");
        boolean replaceWithFake = config.getBoolean("replaceWithFake", false);

        DelayedReplaySelector.SelectionResult selection = DelayedReplaySelector.selectDelayedCopies(
                messageStream, config, numDelayInstances, shiftSendTimestamp, replaceWithFake);

        Set<Integer> capturedIndices = selection.getCapturedIndices();
        List<Goose> delayedMessages = selection.getDelayedMessages();
        List<DelayedReplaySelector.FakeReplacement> fakeReplacements = selection.getFakeReplacements();

        // Build the on-time view of the live window: pass through every legit
        // frame except those captured by the attacker. In F mode, the captured
        // slots are back-filled with the corresponding fake (preserves on-wire
        // continuity but is labelled as attack ground truth).
        ArrayList<Goose> liveWindow = new ArrayList<>(messageStream.size());
        if (replaceWithFake) {
            // index -> fake lookup (each captured index has at most one fake)
            java.util.HashMap<Integer, Goose> fakeByIndex = new java.util.HashMap<>();
            for (DelayedReplaySelector.FakeReplacement fr : fakeReplacements) {
                fakeByIndex.put(fr.index, fr.fake);
            }
            for (int i = 0; i < messageStream.size(); i++) {
                if (capturedIndices.contains(i)) {
                    Goose fake = fakeByIndex.get(i);
                    if (fake != null) {
                        liveWindow.add(fake);
                    }
                    // else: captured but no fake produced (shouldn't happen) -> drop
                } else {
                    liveWindow.add(messageStream.get(i).copy());
                }
            }
        } else {
            for (int i = 0; i < messageStream.size(); i++) {
                if (!capturedIndices.contains(i)) {
                    liveWindow.add(messageStream.get(i).copy());
                }
            }
        }

        // Combine the live window with the delayed replays and order by arrival.
        ArrayList<Goose> combined = new ArrayList<>(liveWindow.size() + delayedMessages.size());
        combined.addAll(liveWindow);
        combined.addAll(delayedMessages);

        if ("send".equalsIgnoreCase(orderBy)) {
            combined.sort(Comparator.comparingDouble(Goose::getTimestamp));
        } else {
            combined.sort(Comparator.comparingDouble(g ->
                    g.getSubscriberRxTs() != null ? g.getSubscriberRxTs() : g.getTimestamp()));
        }

        for (Goose msg : combined) {
            ied.addMessage(msg);
        }

        writeToFile(capturedIndices.size(), messageStream.size());
    }

    private void writeToFile(int captured, int totalMessages) {
        try {
            FileWriter writer = new FileWriter("target/evaluation/evaluation_report.txt", true);
            writer.write("These are the metrics for uc10:\n");
            writer.write("Total messages in the dataset: " + totalMessages + "\n");
            writer.write("Total captured (delayed) frames: " + captured + "\n");
            writer.write("========================================================================\n\n");
            writer.close();
        } catch (IOException e) {
            // best-effort report; ignore
        }
    }
}
