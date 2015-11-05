package server.core;

import network.data.Message;
import server.exceptions.OutputControllerQueueOverflowException;
import server.network.UINetwork;

import java.io.*;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

/**
 * Class created as a part of {@link GameHandler GameHandler} class for controlling the output.
 * <p>
 * This class gathers all output taken from "GameLogic" into a queue, and the process them as the user
 * wishes. <i>Currently passing to {@link server.network.UINetwork UINetwork} and saving in a
 * local file is supported.</i>
 * </p>
 */
public class OutputController implements Runnable {

    private static final int QUEUE_DEFAULT_SIZE = 100000;

    private boolean sendToUI = false;
    private UINetwork uiNetwork;
    private int timeInterval;
    private Timer timer;
    private boolean sendToFile = false;
    private File outputFile;
    private int bufferSize;
    private FileWriter fileWriter;
    private LinkedList<Message> messagesQueue;

    /**
     * Constructor with the properties about how outputs must be handled.
     * <p>
     * This constructor creates an instance of OutputController class with the given parameters.
     * The two booleans indicate that how the class will handle the given outputs.
     * In the case that sendToUI flag is set:
     * <p>
     * Output handling will be assumed as a "Pre Runner" in order to a fast forward run based
     * on the game logic, and storing data on a file for later use.
     * Data would be held on memory until reaches the specified buffer size, and then will be
     * written to the specified file (Using {@link server.core.OutputController.FileWriter FileWriter}
     * runnable class).
     * </p>
     * In the case that sendToFile flag is set:
     * <p>
     * This type of output processing, will store outputs in the object, and sends it in regular
     * timer ticks. This time is specified in milliseconds as timeInterval
     * </p>
     * If the sendToUI or sendToFile flags were set, then the two other values would be assigned to the
     * appropriate instance values.
     * Note that this will raise a runtime exception, in the case of invalid arguments.
     * </p>
     *
     * @param sendToUI     Indicates that data will be sent to given {@link server.network.UINetwork UINetwork} instance
     *                     or not
     * @param uiNetwork    The given instance of {@link server.network.UINetwork UINetwork} class to send data to
     * @param timeInterval The indicated timer tick which triggers
     *                     {@link server.core.OutputController.UINetworkSender#sendToUINetwork(network.data.Message)
     *                     sendToUINetwork(Message)} method
     * @param sendToFile   Indicates that a log of output will be saved in the given {@link java.io.File java.io.File} or
     *                     not
     * @param outputFile   The given {@link java.io.File java.io.File} to save data within
     * @param bufferSize   The preferred number of message elements to be held on memory before writing to file
     */
    public OutputController(boolean sendToUI, UINetwork uiNetwork, int timeInterval
            , boolean sendToFile, File outputFile, int bufferSize) {
        messagesQueue = new LinkedList<>();
        this.sendToUI = sendToUI;
        if (sendToUI) {
            if (uiNetwork != null) {
                this.uiNetwork = uiNetwork;
            } else {
                throw new RuntimeException("The given parameter as uiNetwork is null");
            }

            if (timeInterval > 0) {
                this.timeInterval = timeInterval;
            } else {
                throw new RuntimeException("The given parameter as timeInterval is invalid");
            }
        }
        this.sendToFile = sendToFile;
        if (sendToFile) {
            this.outputFile = outputFile;
            try {
                this.outputFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Could not create log file");
            }

            if (bufferSize > 0 && bufferSize <= QUEUE_DEFAULT_SIZE) {
                this.bufferSize = bufferSize;
            } else if (bufferSize > 0) {
                throw new RuntimeException("The given parameter as bufferSize is greater than max queue size");
            } else {
                throw new RuntimeException("The given parameter as bufferSize is not valid");
            }
        }
    }

    /**
     * Run method implemented from {@link Runnable java.lang.Runnable} class, which will set and run the two type
     * of output handlers.
     * <p>
     * In this method, if the sendToUI mode got activated in the class, then a {@link java.util.Timer Timer} will
     * be scheduled in the given period to send data to {@link server.network.UINetwork UINetwork}.
     * And also if the sendToFile mode got activated, then a thread of
     * {@link server.core.OutputController.FileWriter FileWriter} class will be run.
     * </p>
     */
    @Override
    public void run() {
        if (sendToUI) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new UINetworkSender(), 0, timeInterval);
        }

        if (sendToFile) {
            fileWriter = new FileWriter(outputFile);
        }
        new Thread(fileWriter).start();
    }

    /**
     * Accepts an instance of {@link network.data.Message Message} class as an argument, and places it
     * on the queue.
     * <p>
     * In this method, the given message will be putted in the message queue, only if there's a place on the
     * queue. Otherwise the cleaning and caching processes will be done (through the
     * {@link #hanldeOverflow() hanldeOverflow} method).
     * Also if the buffer size condition is met, then the file writer method will be called with an
     * alternative thread, to save the contents on the file.
     * </p>
     *
     * @param message The given message (as output) to put in the message queue.
     */
    public synchronized void putMessage(Message message) {
        if (messagesQueue.size() <= QUEUE_DEFAULT_SIZE) {
            messagesQueue.addLast(message);
            if (messagesQueue.size() == bufferSize && sendToFile) {
                fileWriter.putMessages(messagesQueue);
                messagesQueue = new LinkedList<>();
            }
        } else {
            if (hanldeOverflow()) {
                messagesQueue.addLast(message);
            } else {
                throw new OutputControllerQueueOverflowException("Could not handle message queue overflow");
            }
        }
        synchronized (messagesQueue) {
            messagesQueue.notifyAll();
        }
    }

    /**
     * Method created to handle the possible overflows occurance in the message queue.
     * <p>
     * TODO: INCOMPLETE - Must be implemented to cache the queue to file.
     * </p>
     *
     * @return True if the queue unblocked, false if any error occur during this operation
     */
    private boolean hanldeOverflow() {
        messagesQueue.clear();
        return true;
    }

    /**
     * Tries to shutdown all the threads ran in this class so far.
     * <p>
     * This method calls the close on the {@link server.core.OutputController.FileWriter FileWriter} instance of
     * the object, causing it to interrupt as soon as all files wrote down on the file.
     * Also cancels the timer to stop automatically invocation of
     * {@link server.core.OutputController.UINetworkSender UINetworkSender}.
     * Other threads would be closed automatically ({@link server.core.OutputController.UINetworkSender
     * UINetworkSenders} after the timeout).
     * </p>
     */
    public void shutdown() {
        if (fileWriter != null)
            fileWriter.close();
        if (timer != null)
            timer.cancel();
    }

    /**
     * This inner class is used to do processes needed during the file saving operations, as an alternative
     * thread.
     * <p>
     * This class will be run by the "OutputController" class, as a file save operation is needed.
     * This Runnable implemented class, could do the long term file saving operations, as an alternative
     * thread.
     * This class uses a {@link java.util.concurrent.BlockingQueue BlockingQueue<LinkedList<Message>>}
     * implementation in order to save files without any block on the way of main thread.
     * </p>
     */
    private class FileWriter implements Runnable {

        private boolean open = false;
        private File file;
        private BlockingQueue<LinkedList<Message>> messagesQueue;

        /**
         * Constructor of the class which accepts a File and sets it as the output of writing operations.
         *
         * @param file Given File to store message data in
         */
        public FileWriter(File file) {
            this.file = file;
            this.messagesQueue = new ArrayBlockingQueue<>(QUEUE_DEFAULT_SIZE);
        }


        /**
         * Puts the given message in the {@link java.util.concurrent.BlockingQueue BlockingQueue} in order to
         * store to file as soon as possible.
         * <p>
         * This method makes the class enable to have a local copy of message queue, during the file
         * processing operations (The outer copy could be destroyed).
         * </p>
         *
         * @param messages The given message linked list, in order to save as a local copy in
         *                 {@link java.util.concurrent.BlockingQueue BlockingQueue}
         */
        public void putMessages(LinkedList<Message> messages) {
            try {
                this.messagesQueue.put(messages);
                synchronized (messagesQueue) {
                    messagesQueue.notifyAll();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Put messages in FileWriter blocking queue interrupted");
            }
        }

        /**
         * The implemented run method of the {@link Runnable java.lang.Runnable} class which will
         * starts the thread listening for any input in order to save to file.
         * <p>
         * This method uses a while loop to save the whole contents of
         * {@link java.util.concurrent.BlockingQueue BlockingQueue} of
         * {@link network.data.Message Messages} to the given file.
         * This will stop working only if the close order were sent and there's no more
         * {@link network.data.Message Messages} on the queue.
         * </p>
         */
        @Override
        public void run() {
            open = true;
            while (open || messagesQueue.size() > 0) {
                writeToFile();
            }
        }

        /**
         * Stores the contents of the message queue as an appendix to the FileWriter file.
         * <p>
         * This method will write the whole contents of the saved message queue in the file writer object
         * on the given file object by object. As this operation is completed, the file writer tries to write the
         * next file in the {@link java.util.concurrent.BlockingQueue BlockingQueue}
         * </p>
         */
        private void writeToFile() {
            try {
                try {
                    OutputStream outputStream = new FileOutputStream(file);
                    OutputStream buffer = new BufferedOutputStream(outputStream);
                    ObjectOutput outputFile = new ObjectOutputStream(buffer);
                    while (!messagesQueue.isEmpty()) {
                        for (Message message : messagesQueue.take()) {
                            outputFile.writeObject(message);
                        }
                    }
                } catch (FileNotFoundException fileNotFound) {
                    throw new RuntimeException("Could not find the log file");
                } catch (IOException ioException) {
                    throw new RuntimeException("Could not write on the log file");
                }
                synchronized (messagesQueue) {
                    messagesQueue.notifyAll();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Taking from FileWriter message queue interrupted.");
            }
        }

        /**
         * This will send to the class a request to close connection to the file.
         * <p>
         * This method sets the open flag of class to <i>false</i> and so as soon as
         * {@link java.util.concurrent.BlockingQueue BlockingQueue} is empty, the writing operation will be
         * finished and the thread will be killed.
         * </p>
         */
        public void close() {
            this.open = false;
        }
    }

    /**
     * Is responsible for sending the first {@link network.data.Message Message} in the queue to the
     * {@link server.network.UINetwork UINetwork}.
     * <p>
     * As the failure in connection and some other issues leaves the
     * {@link server.network.UINetwork#sendBlocking(network.data.Message) UINetwork.send(Message)} method
     * blocked, and so causes a thread block, this class runs as an alternative thread to send the messages
     * in the queue, to the {@link server.network.UINetwork UINetwrok} instance without causing the main
     * thread of OutputController to sleep.
     * </p>
     */
    private class UINetworkSender extends TimerTask {

        private final static int CONNECTION_TIMEOUT = 1000;

        private boolean sent = false;

        /**
         * Implemented run method from {@link java.lang.Runnable Runnable} class which sends the first message in the
         * queue to the {@link server.network.UINetwork UINetwork}.
         * <p>
         * This method will call the {@link #sendToUINetwork(network.data.Message) sendToUINetwork(Message)}
         * method with the appropriate message instance.
         * Every connection made in a separate thread and with a specified timeout.
         * If the last connection could send the message within the timeout, then this method will take another
         * message from the queue and send to the network, otherwise, the last message will be sent again.
         * The caller timer will be canceled if the shutdown request was sent to the class.
         * </p>
         */
        @Override
        public void run() {
            Message message;
            synchronized (messagesQueue) {
                while (messagesQueue.size() <= 0) {
                    try {
                        messagesQueue.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Wait on UINetworkSender interrupted");
                    }
                }
                message = messagesQueue.getFirst();
                if (sent) {
                    messagesQueue.removeFirst();
                }
            }
            sent = false;
            sendToUINetwork(message);
        }

        /**
         * This method serves the instance of {@link server.network.UINetwork UINetwork} class with messages.
         * <p>
         * This method calls {@link server.network.UINetwork#sendBlocking(network.data.Message) send(message)}
         * method on {@link server.network.UINetwork UINetwork} instance in order to show up on UI.
         * Calling this while the message queue is empty, will put it in the wait mode.
         * Basically this method will be called by the timer scheduled according to user preferred
         * time interval.
         * As the time of sending message to the {@link server.network.UINetwork UINetworks} exceeds the
         * timeout limit, then the execution will be cancelled.
         * </p>
         */
        private void sendToUINetwork(Message message) {
            if (sendToUI) {
                Callable<Void> run = () -> {
                    uiNetwork.sendBlocking(message);
                    return null;
                };
                RunnableFuture runnableFuture = new FutureTask<>(run);
                ExecutorService service = Executors.newSingleThreadExecutor();
                service.execute(runnableFuture);
                try {
                    runnableFuture.get(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
                    sent = true;
                } catch (ExecutionException execution) {
                    throw new RuntimeException("Connection to the UI failed in execution");
                } catch (TimeoutException timeOut) {
                    runnableFuture.cancel(true);
                } catch (InterruptedException interrupted) {
                    throw new RuntimeException("Connection to the UI interrupted");
                }
                service.shutdown();
            }
        }
    }
}
