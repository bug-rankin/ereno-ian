package br.ufu.facom.ereno.attacks.uc10.creator;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

public class DelayedReplayCreatorC implements MessageCreator {
    ArrayList<Goose> messageStream;
    private final AttackConfig config;
    private int selectionInterval;
    private int burstInterval;
    private int burstSize;
    private double selectionProb;
    private boolean burstMode;

    public DelayedReplayCreatorC(ArrayList<Goose> messages, AttackConfig config) {
        this.messageStream = messages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numDelayInstances) {

        // config variables
        // Potentially expand upon these parameters and have ranges for them

        // ranges
        int minInterval = config.getNestedInt("selectionInterval", "min", 5);
        int maxInterval = config.getNestedInt("selectionInterval", "max", 25);

        int minBurstInterval = config.getNestedInt("burstInterval", "min", 5);
        int maxBurstInterval = config.getNestedInt("burstInterval", "max", 25);

        int minBurstSize = config.getNestedInt("burstSize", "min", 5);
        int maxBurstSize = config.getNestedInt("burstSize", "max", 25);

        Goose delayMessage; // the potential message to be delayed
        selectionInterval = randomBetween(minInterval, maxInterval); // the rate or interval in which messages are selected to be delayed
        //Logger.getLogger("DelayedReplayCreatorC").info("Selection Interval: " + selectionInterval);
        burstInterval = randomBetween(minBurstInterval, maxBurstInterval);// the interval in which bursts of messages are selected and then delayed       
        burstSize = randomBetween(minBurstSize, maxBurstSize);
        selectionProb = config.getNestedDouble("selectionProb","value",0.5); // determines if a certain messages is delayed or not
        burstMode = config.getBoolean("burstMode", false); // determines if we will do bursts of messages or not
        double minNetworkDelayMs = config.getRangeMin("networkDelayMs", 1.0);
        double maxNetworkDelayMs = config.getRangeMax("networkDelayMs", 31.0);
        if (minNetworkDelayMs <= 0 || maxNetworkDelayMs <= 0 || maxNetworkDelayMs < minNetworkDelayMs) {
            minNetworkDelayMs = 1.0;
            maxNetworkDelayMs = 31.0;
        }
        boolean shiftSendTimestamp = config.getBoolean("shiftSendTimestamp", true);
        String orderBy = config.getString("orderBy", "received");

        // counter/tracking variables that are not assigned in the config file
        int burstMessageCounter = 0; // keeps track of the amount of messages grabbed before the burst maximum is reached
        int burstIntervalCounter = 0; // ensures that we have separate bursts. Ensures we maintain the burst interval
        double selectionValue = 0.0;
        int selectionIntervalCounter = 0;

        int faultCounter = 0;
        ArrayList<Goose> delayedMessages = new ArrayList<>();

        // Store the parameter values in some txt file or in the csv file


        for (int i = 0; numDelayInstances > 0 & i < messageStream.size(); i++) {
            
            delayMessage = messageStream.get(i);
            //Logger.getLogger("DelayedReplayCreatorC").info("Captured the legitimate message at " + delayMessage.getTimestamp());

            // check to see if it is faulty

            // if statement to check if we want bursts
            if (burstMode == true & delayMessage.getCbStatus() == 1) { // condense to having one condition statement once behavior is confirmed
                // have another condition that grabs a certain amount of faulty messages depending on burst size, checks if the burstIntervalCounter is 0, and checks if the counter is less than burst size
                // perform further testing to see if we need to account for testing

                faultCounter++;

                if (burstIntervalCounter == burstInterval) { // once we have waited for the set interval, begin the next burst of messages
                    burstMessageCounter = 0;
                    burstIntervalCounter = 0;
                } else if (burstMessageCounter == burstSize) { // once we reach the burst size, ensure that we enact an the set interval until we start the next burst
                    burstIntervalCounter++;
                    continue;
                }

                double attackDelaySeconds = randomBetween(minNetworkDelayMs, maxNetworkDelayMs) / 1000.0;

                //int closestIndex = getClosestIndex(delayMessage, networkDelay, currentIndex);
                //Goose closestMessage = messageStream.get(closestIndex);

                Goose delayedGoose = applyDelay(delayMessage, attackDelaySeconds, shiftSendTimestamp);
                delayedMessages.add(delayedGoose);

                /*
                if (delayedTimestamp >= closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetWriter.label[9]);

                    //messageStream.add(closestIndex+1, delayMessage);
                    //messageStream.remove(currentIndex);

                    ied.addMessage(delayMessage);
                } else if (delayedTimestamp < closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetWriter.label[9]);

                    //messageStream.add(closestIndex-1, delayMessage);
                    //messageStream.remove(currentIndex);

                    ied.addMessage(delayMessage);
                }
                 */
                numDelayInstances--;
                burstMessageCounter++;

            } else if (burstMode == false & delayMessage.getCbStatus() == 1) { // if burstmode is false, then grab singular messages
                // later on potentially include the selection interval in this condition, will check if the interval counter is 0
                // for the burst, delay each message by a separate amount

                faultCounter++;

                if (selectionIntervalCounter == selectionInterval) {
                    selectionIntervalCounter = 0;
                } else if (selectionIntervalCounter < selectionInterval & selectionIntervalCounter >= 1) {
                    selectionIntervalCounter++;
                    continue;
                }

                // have the randomBetween var here for the selection rate
                // this will ensure the random between is only called for faulty messages
                selectionValue = randomBetween(0.0, 1.0);
                
                if (selectionValue > selectionProb) {
                    continue;
                }
                
                double attackDelaySeconds = randomBetween(minNetworkDelayMs, maxNetworkDelayMs) / 1000.0;

                //int closestIndex = getClosestIndex(delayMessage, networkDelay, currentIndex);
                //Goose closestMessage = messageStream.get(closestIndex);

                Goose delayedGoose = applyDelay(delayMessage, attackDelaySeconds, shiftSendTimestamp);
                delayedMessages.add(delayedGoose);
                /*
                if (delayedTimestamp >= closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetWriter.label[9]);

                    //messageStream.add(closestIndex+1, delayMessage);
                    //messageStream.remove(currentIndex);
                    ied.addMessage(delayMessage);
                    //messageStream.get(currentIndex).setLabel("faulty_not_delayed");
                } else if (delayedTimestamp < closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetWriter.label[9]);

                    ied.addMessage(delayMessage);
                    //messageStream.set(currentIndex, delayMessage); // for debugging, add the delayed message at the index after currentIndex
                }
                */
                numDelayInstances--;
                selectionIntervalCounter++;
            }
            // if not faulty, then continue to the next message and check

        }

        if ("send".equalsIgnoreCase(orderBy)) {
            delayedMessages.sort(Comparator.comparingDouble(Goose::getTimestamp));
        } else {
            delayedMessages.sort(Comparator.comparingDouble(g ->
                    g.getSubscriberRxTs() != null ? g.getSubscriberRxTs() : g.getTimestamp()));
        }

        for (Goose delayedMessage : delayedMessages) {
            ied.addMessage(delayedMessage);
        }

        writeToFile(faultCounter, messageStream.size());

    }

    private void writeToFile(int faults, int totalMessages) {
        try {
            FileWriter writer = new FileWriter("target/evaluation/evaluation_report.txt", true);
            writer.write("These are the metrics for uc10:\n");
            writer.write("Total messages in the dataset: " + totalMessages + "\n");
            writer.write("The selection interval: " + getSelectionInterval() + "\n");
            writer.write("The burst interval: " + getBurstInterval() + "\n");
            writer.write("The burst size: " + getBurstSize() + "\n");
            writer.write("The selection probability: " + getSelectionProb() + "\n");
            writer.write("The burst mode: " + getBurstMode() + "\n");
            writer.write("Total amount of faulty messages: " + faults + "\n");
            writer.write("========================================================================\n\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double getNetworkDelay() {
        return randomBetween(0.001, 0.031); // no idea how he got these bounds for the network delay, will use this method for consistency with the rest of the project
    }

    private double getNetworkDelayModified() {
        return randomBetween(0.200, 0.700);
    }

    private int getClosestIndex(Goose currentMessage, double networkDelay, int startIndex) { // implement later based on network delay

        int size = messageStream.size();
        int closestIndex = startIndex;
        double min = messageStream.get(size - 1).getTimestamp() - (currentMessage.getTimestamp() + networkDelay);
        int breakCounter = 0;

        for (int i = startIndex + 1; i < size; i++) {
            if (min > Math.abs(messageStream.get(i).getTimestamp() - (currentMessage.getTimestamp() + networkDelay))) {
                min = Math.abs(messageStream.get(i).getTimestamp() - (currentMessage.getTimestamp() + networkDelay));
                closestIndex = i;
                breakCounter = 0;
            } else if (breakCounter == 3) { // ensures that we do not go through the entire message stream unnecessarily
                break;
            } else if (min < Math.abs(messageStream.get(i).getTimestamp() - (currentMessage.getTimestamp() + networkDelay))) {
                breakCounter++;
            }
        }

        return closestIndex;
    }

    public int getSelectionInterval() { return selectionInterval; }

    public int getBurstInterval() { return burstInterval; }

    public int getBurstSize() { return burstSize; }

    public double getSelectionProb() { return selectionProb; }

    public boolean getBurstMode() { return burstMode; }

    private Goose applyDelay(Goose originalMessage, double attackDelaySeconds, boolean shiftSendTimestamp) {
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

    // add more if more parameters are added to this attack

}
