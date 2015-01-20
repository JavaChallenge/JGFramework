package server.network;

import server.network.data.Message;
import util.Log;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * {@link server.network.UINetwork} is a server which is responsible for sending
 * UI data to the <code>node.js</code> client.
 * <p>
 * When a client is connected to the server, it sends a token and waits for the
 * initial message which contains necessary data of beginning of the game.
 * <p>
 * Messages are sent using {@link #send} method.
 * <p>
 * The communications are one sided, i.e. everything which is sent by client is
 * ignored by the server.
 */
public final class UINetwork extends NetServer {

    /**
     * Logging tag
     */
    private static String TAG = "UINetwork";

    /**
     * token of the server
     */
    private final String mToken;

    /**
     * current client of the server
     */
    private JsonSocket mClient;

    /**
     * lock for {@link #mClient}
     */
    private final Object mClientLock;

    /**
     * a deque for messages that are waiting to send
     */
    private LinkedBlockingDeque<Message> mMessagesToSend;

    /**
     * thread executor which is used to accept clients
     */
    private ExecutorService executor;

    /**
     * Initializes the class and starts sending messages to clients.
     * If there is no client at the time of sending, the message will be
     * thrown away.
     *
     * @param token    token of the server
     * @see #send
     * @see #startSending
     * @see #hasClient
     * @see #waitForClient
     * @see #waitForNewClient
     */
    public UINetwork(String token) {
        mToken = token;
        mClientLock = new Object();
        mMessagesToSend = new LinkedBlockingDeque<>();
        executor = Executors.newCachedThreadPool();
        startSending();
    }

    /**
     * Sends a message to the client.
     * It actually adds this message to {@link #mMessagesToSend} and sends is
     * as soon as it is possible.
     *
     * @param msg    message to send
     */
    public void send(Message msg) {
        mMessagesToSend.add(msg);
    }

    /**
     * Runs the main thread for sending messages to the client on the
     * {@link #executor}.
     * If there is no client at the time of sending, the message will be
     * thrown away.
     * To prevent this situation one can use {@link #waitForClient} to ensure
     * that a client is connected to the server.
     *
     * @see #waitForClient
     * @see #waitForNewClient
     * @see #send
     */
    private void startSending() {
        executor.submit(() -> {
            while (!isTerminated()) {
                try {
                    Message msg = mMessagesToSend.take();
                    synchronized (mClientLock) {
                        mClient.send(msg);
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "waiting for message interrupted", e);
                } catch (IOException e) {
                    Log.d(TAG, "message sending failure", e);
                }
            }
        });
    }

    /**
     * Creates a new thread to verify the client by taking a token.
     *
     * @param client    a {@link server.network.JsonSocket} which is connected
     * @see server.network.NetServer#accept
     */
    @Override
    protected void accept(JsonSocket client) {
        executor.submit(() -> {
            try {
                verifyClient(client);
            } catch (Exception e) {
                // if anything was wrong close the client!
                Log.i(TAG, "client rejected", e);
                try {
                    client.close();
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Verifies the client by taking a token.
     *
     * @param client    client
     * @throws Exception if verification is failed
     * @see #accept
     */
    private void verifyClient(JsonSocket client) throws Exception {
        // get the token, timeout is 10 seconds
        Future<Message> futureMessage
                = executor.submit(() -> client.get(Message.class));
        Message token = futureMessage.get(10, TimeUnit.SECONDS);
        // check the token
        if (!"token".equals(token.name) || !mToken.equals(token.args[0]))
            throw new Exception("UINetwork: Client rejected, " +
                    "token is not correct.");
        // so the token is correct!
        changeClient(client);
    }

    /**
     * Changes current client to the specified client.
     * It actually closes the previous client (if exists) and then creates a
     * thread for the new one.
     *
     * @param client    new client
     * @see #verifyClient
     */
    private void changeClient(JsonSocket client) {
        synchronized (mClientLock) {
            try {
                // close previous socket
                mClient.close();
            } catch (Exception e) {
                Log.i(TAG, "socket closing failure", e);
            } finally {
                // change the client
                mClient = client;
                // notify waiting threads
                mClientLock.notify();
            }
        }
    }

    /**
     * Returns true if any client is connected and verified by the server.
     *
     * @return true if there is any clients
     */
    public boolean hasClient() {
        return mClient != null;
    }

    /**
     * Caller will be blocked until a client is connected.
     * If currently a client is connected, returns without waiting.
     *
     * @throws InterruptedException if the current thread is interrupted.
     */
    public void waitForClient() throws InterruptedException {
        synchronized (mClientLock) {
            if (mClient != null)
                return;
            mClientLock.wait();
        }
    }

    /**
     * Caller will be blocked until a client is connected or the
     * timeout is reached.
     * If currently a client is connected, returns without waiting.
     *
     * @param timeout   timeout in seconds
     * @throws InterruptedException if the current thread is interrupted.
     */
    public void waitForClient(long timeout) throws InterruptedException {
        synchronized (mClientLock) {
            if (mClient != null)
                return;
            mClientLock.wait(timeout);
        }
    }

    /**
     * Caller will be blocked until a <b>new</b> client is connected.
     *
     * @throws InterruptedException if the current thread is interrupted.
     */
    public void waitForNewClient() throws InterruptedException {
        synchronized (mClientLock) {
            mClientLock.wait();
        }
    }

    /**
     * Caller will be blocked until a <b>new</b> client is connected or the
     * timeout is reached.
     *
     * @param timeout   timeout in seconds
     * @throws InterruptedException if the current thread is interrupted.
     */
    public void waitForNewClient(long timeout) throws InterruptedException {
        synchronized (mClientLock) {
            mClientLock.wait(timeout);
        }
    }

}