package server.network;

import network.JsonSocket;
import network.data.Message;
import network.data.ReceivedMessage;
import util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;

/**
 * {@link server.network.ClientHandler} handles a client, i.e. it is responsible
 * for sending/receiving messages to/from the client.
 * <p>
 * After that a client is verified by the server, server assigns a
 * {@link server.network.ClientHandler} to that client.
 * <p>
 * Messages should arrive in a predefined interval of time (which is determined
 * by the server). Every message arrived out of this interval is discarded
 * immediately. If two or more messages arrived in the valid time the last one
 * is stored as the "last validated message".
 */
public class ClientHandler {

    /**
     * Maximum number of exceptions during connection.
     */
    public static final int MAX_NUM_EXCEPTIONS = 20;

    /**
     * Logging tag.
     */
    private static String TAG = "ClientHandler";

    /**
     * Socket of the client.
     */
    private JsonSocket client;

    /**
     * Lock for {@link #client}.
     */
    private final Object clientLock;

    /**
     * Termination flag.
     */
    private boolean terminateFlag;

    /**
     * Last valid message which is arrived on time.
     */
    private ReceivedMessage lastValidatedMessage;

    /**
     * Last message received from client.
     */
    private ReceivedMessage lastReceivedMessage;

    /**
     * This object is notified when a message is received.
     */
    private final Object messageNotifier;

    /**
     * Message queue. These messages will be sent to the client asap.
     */
    private final LinkedBlockingDeque<Message> messagesToSend;

    /**
     * Message queue.
     */
    private final ArrayList<Message> messagesQueued;

    /**
     * Number of exceptions occurred during communication.
     */
    private int numOfExceptions;


    /**
     * Constructor.
     */
    public ClientHandler() {
        messagesToSend = new LinkedBlockingDeque<>();
        messagesQueued = new ArrayList<>();
        clientLock = new Object();
        messageNotifier = new Object();
    }

    /**
     * Queues a message for the client. Message is not sent until {@link #send}
     * is called.
     *
     * @param msg message to send.
     */
    public void queue(Message msg) {
        synchronized (messagesQueued) {
            messagesQueued.add(msg);
        }
    }

    /**
     * Sends last queued message.
     */
    public void send() {
        synchronized (messagesQueued) {
            messagesToSend.addAll(messagesQueued);
            messagesQueued.clear();
        }
    }

    /**
     * Returns a runnable which sends messages when it is ran.
     */
    public Runnable getSender() {
        return () -> {
            while (!terminateFlag) {
                try {
                    Message msg = messagesToSend.take();
                    client.send(msg);
                } catch (Exception e) {
                    Log.i(TAG, "Message sending failure", e);
                }
            }
        };
    }

    /**
     * Removes last validated message.
     */
    public void clearLastValidatedMessage() {
        lastValidatedMessage = null;
    }

    /**
     * A message is valid if it is arrived in a valid time, which is determined
     * by the server.
     *
     * @return last validated message.
     * @see #getReceiver
     */
    public ReceivedMessage getLastValidatedMessage() {
        return lastValidatedMessage;
    }

    /**
     * Binds handler to a socket, i.e. this handler is responsible for
     * sending/receiving messages to/from the socket.
     *
     * @param socket client
     */
    public void bind(JsonSocket socket) {
        try {
            if (client != null)
                client.close();
        } catch (IOException e) {
            Log.i(TAG, "socket closing failure", e);
            handleIOE(e);
        } finally {
            synchronized (clientLock) {
                client = socket;
                clientLock.notifyAll();
            }
        }
    }

    /**
     * The result of method is a {@link java.lang.Runnable} object. When this
     * runnable is called it receives a new message from the client and if it
     * arrives in a valid time (which is checked using <code>timeValidator</code>)
     * stores it in {@link #lastValidatedMessage}.
     *
     * @param timeValidator <code>get</code> method of this object returns
     *                      true if and only if it is called in a valid time.
     *                      (valid time is the time when messages can be
     *                      arrived from clients, e.g. half a second after
     *                      each turn)
     * @return a runnable which is used by server to receive new messages of client
     */
    public Runnable getReceiver(Supplier<Boolean> timeValidator) {
        return () -> {
            while (!terminateFlag) {
                try {
                    waitForClient();
                    receive();
                    if (timeValidator.get())
                        lastValidatedMessage = lastReceivedMessage;
                } catch (InterruptedException e) {
                    Log.i(TAG, "waiting for client interrupted", e);
                } catch (IOException e) {
                    Log.i(TAG, "message receiving failure", e);
                    handleIOE(e);
                } catch (Exception e) {
                    Log.i(TAG, "message receiving failure", e);
                }
            }
        };
    }

    /**
     * Receives a message from client.
     *
     * @throws IOException if an I/O error occurs.
     */
    private void receive() throws IOException {
        if (terminateFlag)
            return;
        lastReceivedMessage = client.get(ReceivedMessage.class);
        synchronized (messageNotifier) {
            messageNotifier.notifyAll();
        }
    }

    /**
     * Returns true if any client is connected to this client handler.
     *
     * @return true if any client is connected to this client handler.
     */
    public boolean isConnected() {
        return client != null;
    }

    /**
     * Blocks caller method until the client send a message.
     *
     * @throws InterruptedException if current thread is interrupted.
     */
    public void waitForClientMessage() throws InterruptedException {
        synchronized (messageNotifier) {
            messageNotifier.wait();
        }
    }

    /**
     * Blocks caller method at most <code>timeout</code> milliseconds until
     * the client send a message.
     *
     * @throws InterruptedException if current thread is interrupted.
     */
    public void waitForClientMessage(long timeout) throws InterruptedException {
        synchronized (messageNotifier) {
            messageNotifier.wait(timeout);
        }
    }

    /**
     * Blocks caller method until a client is connected to this handler.
     *
     * @throws InterruptedException if current thread is interrupted.
     */
    public void waitForClient() throws InterruptedException {
        if (client == null || client.isClosed())
            synchronized (clientLock) {
                clientLock.wait();
            }
    }

    /**
     * Blocks caller method at most <code>timeout</code> milliseconds until a
     * client is connected to this handler.
     *
     * @param timeout timeout in milliseconds
     * @throws InterruptedException if current thread is interrupted.
     */
    public void waitForClient(long timeout) throws InterruptedException {
        if (client == null || client.isClosed())
            synchronized (clientLock) {
                clientLock.wait(timeout);
            }
    }

    /**
     * Terminates operations of the handler. It actually closes the socket and
     * changes a flag.
     */
    public void terminate() {
        try {
            terminateFlag = true;
            if (client != null)
                client.close();
        } catch (IOException e) {
            Log.i(TAG, "Socket closing failure.", e);
        }
    }

    /**
     * Handles I/O Exceptions.
     *
     * @param e exception
     */
    public void handleIOE(IOException e) {
        numOfExceptions++;
        if (numOfExceptions > MAX_NUM_EXCEPTIONS)
            terminate();
    }

}
