package server.core;

import server.exceptions.OutputControllerQueueOverflowException;
import server.network.UINetwork;
import server.network.data.Message;

import java.io.File;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class created as a part of {@link GameHandler GameHandler} class for controlling the output.
 * <p>
 *     This class gathers all output taken from "GameLogic" into a queue, and the process them as the user
 *     wishes. <i>Currently passing to {@link server.network.UINetwork UINetwork} and saving in a
 *     local file is supported.</i>
 * </p>
 */
public class OutputController implements Runnable {

    private static final int QUEUE_DEFAULT_SIZE = 1000;

    private UINetwork uiNetwork;
    private int timeInterval;
    private Timer timer;
    private File outputFile;
    private int bufferSize;
    private FileWriter fileWriter;
    private OutputHandleMode outputHandleMode;
    private LinkedList<Message> messagesQueue;

    public OutputController() {
        messagesQueue = new LinkedList<Message>();
    }

    /**
     * Run method implemented from {@link Runnable java.lang.Runnable} class.
     * <p>
     *     This method runs the class activities. In order to running the UINetwork timer correctly, must
     *     be called after initializing using one of the <i>"setOutputHandleMode"</i> methods.
     * </p>
     */
    @Override
    public void run() {
        if (outputHandleMode == OutputHandleMode.UI_NETWORK) {
            if (timeInterval != 0) {
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        sendToUINetwork();
                    }
                }, 0, timeInterval);
            }
            else {
                throw new RuntimeException("Output controller, did not initialized yet");
            }
        }
    }

    /**
     * Sets the output processing mode of class, to "Save to file" mode.
     * <p>
     *     This method will set the behavior of class, as a "Pre Runner" in order to a fast forward run based
     *     on the game logic, and storing data on a file for later usage.<br>
     *     Data would be hold on main memory until reaches the specified buffer size, and then will be
     *     written to the specified file (Using {@link server.core.OutputController.FileWriter FileWriter}
     *     runnable class).
     * </p>
     * @param file File to save outputs of processes to
     * @param bufferSize Size which data will be held on RAM, before accessing the file
     */
    public void setOutputHandleMode(File file, int bufferSize) {
        outputFile = file;
        if (bufferSize > 0) {
            this.bufferSize = bufferSize;
        } else if (bufferSize >= QUEUE_DEFAULT_SIZE) {
            throw new RuntimeException("Buffer size greater than max queue size.");
        } else {
            throw new RuntimeException("Invalid buffer size.");
        }
        fileWriter = new FileWriter(file);
        outputHandleMode = OutputHandleMode.FILE;
    }

    /**
     * Sets the output processing mode of class, to "Show on UI" mode.
     * <p>
     *     This type of output processing, will store outputs in the object, and sends it in regular
     *     timer ticks. This time is specified in milliseconds as "timeInterval".
     * </p>
     * @param uiNetwork The instance of {@link server.network.UINetwork UINetwork} class, which output would be
     *                  sent to
     * @param timeInterval The ratio of sending outputs to the UI
     */
    public void setOutputHandleMode(UINetwork uiNetwork, int timeInterval) {
        this.uiNetwork = uiNetwork;
        this.timer = new Timer("UI_Network_Sender");
        this.timeInterval = timeInterval;
        outputHandleMode = OutputHandleMode.UI_NETWORK;
    }

    /**
     * Accepts an instance of {@link server.network.data.Message Message} class as an argument, and places it
     * on the queue.
     * <p>
     *     In this method, the given message will be putted in the message queue, only if there's a place on the
     *     queue. Otherwise the cleaning and caching processes will be done (through the
     *     {@link #hanldeOverflow() hanldeOverflow} method).
     *     Also if the buffer size condition is met, then the file writer method will be called with an
     *     alternative thread, to save the contents on the file.
     * </p>
     * @param message The given message (as output) to put in the message queue.
     */
    public synchronized void putMessage(Message message) {
        if (messagesQueue.size() < QUEUE_DEFAULT_SIZE) {
            messagesQueue.addLast(message);
            if (messagesQueue.size() == bufferSize && outputHandleMode == OutputHandleMode.FILE) {
                try {
                    while (fileWriter.isBusy()) {
                        wait();
                    }
                    fileWriter.setMessages((LinkedList<Message>) messagesQueue.clone());
                    new Thread(fileWriter).start();
                    messagesQueue.clear();
                } catch (Exception e) { // TODO: Change to appropriate exception handler

                }
            }
        } else {
            if (hanldeOverflow()) {
                messagesQueue.addLast(message);
            } else {
                throw new OutputControllerQueueOverflowException("Could not handle message queue overflow");
            }
        }
        notifyAll();
    }

    /**
     * Method created to handle the possible overflows occurance in the message queue.
     * <p>
     *
     * </p>
     * @return True if the queue unblocked, false if any error occur during this operation
     */
    private boolean hanldeOverflow() {
        //TODO: Decide what to do with queue

        messagesQueue.clear();
        return true;
    }

    /**
     * This inner class is used to do processes needed during the file saving operations, as an alternative
     * thread.
     * <p>
     *     This class will be run by the "OutputController" class, as a file save operation is needed.
     *     This Runnable implemented class, could do the long term file saving operations, as an alternative
     *     thread.
     *     Note that this class could just do one file operation at a time.
     * </p>
     */
    private class FileWriter implements Runnable {
        //TODO: Insurance about DEAD LOCK in huge files with multiple threads

        private boolean busy = false;
        private File file;
        private LinkedList<Message> messages;

        /**
         * Constructor of the class which accepts a File and sets it as the output of writing operations.
         * @param file Given File to store message data in
         */
        public FileWriter(File file) {
            this.file = file;
        }

        /**
         * Sets the message queue temp (which is stored in the object).
         * <p>
         *     This method makes the class enable to have a local copy of message queue, during the file
         *     processing operations (The outer copy could be destroyed).
         * </p>
         * @param messages The given message linked list, in order to save as a local copy
         */
        public void setMessages(LinkedList<Message> messages) {
            this.messages = messages;
        }

        /**
         * The implemented run method of the {@link Runnable java.lang.Runnable} class which will
         * blocks the other connections to the file writer (by setting <i>"busy"</i> boolean as true) and
         * calls the {@link #writeToFile() writeToFile} method in order to writes the contents on the file.
         */
        @Override
        public void run() {
            busy = true;
            writeToFile();
        }

        /**
         * Stores the contents of the message queue as an appendix to the FileWriter file.
         * <p>
         *     This method will write the whole contents of the saved message queue in the file writer object
         *     on the given file. As this operation is completed, the file writer is eligible to accept files
         *     again.
         * </p>
         */
        private synchronized void writeToFile() {
            for (Message message : this.messages) {
                //TODO: Save messages in a file
            }
            busy = false;
            notifyAll();
        }

        public boolean isBusy() {
            return busy;
        }
    }

    /**
     * This method serves the instance of {@link server.network.UINetwork UINetwork} class with messages.
     * <p>
     *     This method calls {@link server.network.UINetwork#send(server.network.data.Message) send(message)}
     *     method on {@link server.network.UINetwork UINetwork} instance in order to show up on UI.
     *     Calling this while the message queue is empty, will put it in the wait mode.
     *     Basically this method will be called by the timer scheduled according to user preferred
     *     time interval.
     * </p>
     */
    private synchronized void sendToUINetwork() {
        while (messagesQueue.size() <= 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException("Sending to UINetwork interrupted");
            }
        }
        uiNetwork.send(messagesQueue.getFirst()); //TODO: Must be added in UINetwork
        messagesQueue.removeFirst();
        notifyAll();
    }

    /**
     * Holds supported types of output handling modes.
     */
    private enum OutputHandleMode {
        FILE(),
        UI_NETWORK()
    }
}
