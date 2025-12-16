package br.ufu.facom.ereno.attacks.uc10.creator;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.benign.uc00.devices.LegitimateProtectionIED;
import br.ufu.facom.ereno.api.SetupIED;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.util.Labels;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/**
 * @author Luke Haidze
 */
public class DelayedReplayCreator implements MessageCreator {
    ArrayList<Goose> messageStream;
    Goose seedMessage;
    int originalSize;
    //long maxTime = Integer.parseInt(SetupIED.ECF.maxTime);
    /**
     * @param messages - previously generated legitimate messages
     */
    public DelayedReplayCreator(ArrayList<Goose> messages) {
        this.messageStream = messages;
        this.originalSize = messages.size();
    }

    /**
     * @param ied               - IED that will receive the generated messages
     * @param numDelayInstances - number of fault instances
     */
    @Override
    public void generate(IED ied, int numDelayInstances) {

        // go through message stream and find faulty ones. Then delay those faulty messages

        // potentially add some variables to help out
        Goose delayMessage; // the potential message to be delayed
        int selectionInterval = 3; // the rate or interval in which messages are selected to be delayed
        int burstInterval = 5; // the interval in which bursts of messages are selected and then delayed
        int burstSize = 5; // the size of a message burst
        //int spacing; // the spacing between delayed messages (might be redundant due to the amount of delay and the timestamps
        double selectionRate = 0.4; // determines if a certain messages is delayed or not
        boolean burstMode = false; // determines if we will do bursts of messages or not
        //int numLegitDelays; // the number of legitimate messages to be delayed // honestly, probably not needed either
        //float delayedFaultProportion; // the proportion of delayed faulty messages to the entirety of the message stream // probably not needed
        //int startOfRange; // start of a specific range to select messages from // may be redundant
        //int endOfRange; // end of a specific range to select messages from // me be redundant

         int burstMessageCounter = 0; // keeps track of the amount of messages grabbed before the burst maximum is reached
         int burstIntervalCounter = 0; // ensures that we have separate bursts. Ensures we maintain the burst interval
        double selectionValue = 0.0;
        int selectionIntervalCounter = 0;

        /*
        for  (int i = 0; i < numDelayInstances; i++) {
            messageStream.get(i).setLabel(GSVDatasetWriter.label[9]);
            ied.addMessage(messageStream.get(i));
        }
        */
        
        for (int i = 0; numDelayInstances > 0 & i < messageStream.size(); i++) {

            delayMessage = messageStream.get(i);

            // check to see if it is faulty

            // if statement to check if we want bursts
            if (burstMode & delayMessage.getCbStatus() == 1 & !delayMessage.getLabel().equals(GSVDatasetwriter.label[9])) { // condense to having one condition statement once behavior is confirmed
                // have another condition that grabs a certain amount of faulty messages depending on burst size, checks if the burstIntervalCounter is 0, and checks if the counter is less than burst size
                // perform further testing to see if we need to account for testing
                if (burstIntervalCounter == burstInterval) { // once we have waited for the set interval, begin the next burst of messages
                    burstMessageCounter = 0;
                    burstIntervalCounter = 0;
                } else if (burstMessageCounter == burstSize) { // once we reach the burst size, ensure that we enact an the set interval until we start the next burst
                    burstIntervalCounter++;
                    continue;
                }

                double networkDelay = getNetworkDelay();
                int currentIndex = i;

                int closestIndex = getClosestIndex(delayMessage, networkDelay, currentIndex);
                Goose closestMessage = messageStream.get(closestIndex);
                
                double delayedTimestamp = delayMessage.getTimestamp() + networkDelay;

                if (delayedTimestamp >= closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetwriter.label[9]);

                    messageStream.add(closestIndex+1, delayMessage);
                    messageStream.remove(currentIndex);
                } else if (delayedTimestamp < closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetwriter.label[9]);

                    messageStream.add(closestIndex-1, delayMessage);
                    messageStream.remove(currentIndex);
                }
                numDelayInstances--;
                burstMessageCounter++;

            } else if (!burstMode & delayMessage.getCbStatus() == 1 & !delayMessage.getLabel().equals(GSVDatasetWriter.label[9])) { // if burstmode is false, then grab singular messages
                // later on potentially include the selection interval in this condition, will check if the interval counter is 0
                // for the burst, delay each message by a separate amount

                if (selectionIntervalCounter != 0 & selectionIntervalCounter < selectionInterval) {
                    selectionIntervalCounter++;
                    continue;
                } else if (selectionIntervalCounter == selectionInterval) {
                    selectionIntervalCounter = 0;
                }

                // have the randomBetween var here for the selection rate
                // this will ensure the random between is only called for faulty messages
                selectionValue = randomBetween(0.0, 1.0);
                if (selectionValue < selectionRate) {
                    continue;
                }


                double networkDelay = getNetworkDelay();
                int currentIndex = i;
                
                int closestIndex = getClosestIndex(delayMessage, networkDelay, currentIndex);
                Goose closestMessage = messageStream.get(closestIndex);

                double delayedTimestamp = delayMessage.getTimestamp() + networkDelay;

                if (delayedTimestamp >= closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetwriter.label[9]);

                    messageStream.add(closestIndex+1, delayMessage);
                    messageStream.remove(currentIndex); // for debugging do not remove, just set a different label
                    //messageStream.get(currentIndex).setLabel("faulty_not_delayed");
                } else if (delayedTimestamp < closestMessage.getTimestamp()) {
                    delayMessage.setTimestamp(delayedTimestamp);
                    delayMessage.setLabel(GSVDatasetwriter.label[9]);

                    messageStream.set(currentIndex, delayMessage); // for debugging, add the delayed message at the index after currentIndex
                }
                
                numDelayInstances--;
                selectionIntervalCounter++;
            } 

            // if not faulty, then continue to the next message and check

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

}
