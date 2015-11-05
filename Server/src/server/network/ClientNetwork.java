package server.network;

import com.google.gson.Gson;
import model.Event;
import network.JsonSocket;
import network.data.Message;
import network.data.ReceivedMessage;
import util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

/**
 * {@link server.network.ClientNetwork} is a server which is responsible for
 * sending messages from server to players
 * and receiving players' requests (messages).
 * <p>
 * First of all clients (players) must be defined to the server via method
 * {@link #defineClient}. Server assigns an ID to each client so further calls
 * get ID of the client instead of its token.
 * <p>
 * When a client (player) is connected to the server, it sends a token and waits
 * for the initial message which contains necessary data of beginning of the
 * game.
 * <p>
 * Each turn server sends some data to the clients, these data are first queued
 * by {@link #queue} and then sent by send method. Server does it best to send
 * messages simultaneously to clients, i.e. sending procedure is fair.
 * <p>
 * The communications are one sided, i.e. everything which is sent by client is
 * ignored by the server.
 */
public class ClientNetwork extends NetServer {

    /**
     * Logging tag.
     */
    private static String TAG = "ClientNetwork";

    /**
     * Indicates that receive time is valid or not.
     */
    private volatile boolean receiveTimeFlag;

    /**
     * Tokens of clients.
     */
    private HashMap<String, Integer> mTokens;

    /**
     * Client handlers.
     */
    private ArrayList<ClientHandler> mClients;

    /**
     * A thread pool used to send all messages.
     */
    private ExecutorService sendExecutor;

    /**
     * A thread pool used to receive messages from clients.
     */
    private ExecutorService receiveExecutor;

    /**
     * A thread pool used to accept and verify clients.
     */
    private ExecutorService acceptExecutor;

    /**
     * Gson used to extract event from a message.
     */
    private Gson gson;

    /**
     * Constructor.
     */
    public ClientNetwork() {
        gson = new Gson();
        mTokens = new HashMap<>();
        mClients = new ArrayList<>();
        sendExecutor = Executors.newCachedThreadPool();
        receiveExecutor = Executors.newCachedThreadPool();
    }

    /**
     * Defines a client with a token. It actually assigns an ID and a handler to
     * the client.
     *
     * @param token token of the client
     * @return ID of the client
     * @see {@link #omitAllClients}
     */
    public int defineClient(String token) {
        if (!isTerminated())
            throw new RuntimeException("Server is not terminated when defineClient() is called.");
        if (mTokens.containsKey(token))
            throw new RuntimeException("Duplicate token. " + token);
        int id = mClients.size();
        mTokens.put(token, id);
        mClients.add(newClient());
        return id;
    }

    /**
     * Creates a new handler.
     *
     * @return new handler
     */
    private ClientHandler newClient() {
        ClientHandler client = new ClientHandler();
        Runnable receiver = client.getReceiver(() -> receiveTimeFlag);
        receiveExecutor.submit(receiver);
        sendExecutor.submit(client.getSender());
        return client;
    }

    /**
     * Remove defined clients and free memory allocated for them.
     *
     * @see {@link #defineClient}
     */
    public void omitAllClients() {
        if (!isTerminated())
            throw new RuntimeException("Server is not terminated when omitAllClients() is called.");
        mClients.forEach(server.network.ClientHandler::terminate);
        mTokens.clear();
        mClients.clear();
        sendExecutor.shutdownNow();
        receiveExecutor.shutdownNow();
        System.gc();
        mTokens = new HashMap<>();
        mClients = new ArrayList<>();
        sendExecutor = Executors.newCachedThreadPool();
        receiveExecutor = Executors.newCachedThreadPool();
    }

    /**
     * Queues a message for a client.
     *
     * @param clientID ID of the client
     * @param msg      message
     * @see {@link #defineClient}
     * @see {@link #sendAllBlocking}
     */
    public void queue(int clientID, Message msg) {
        mClients.get(clientID).queue(msg);
    }

    /**
     * Sends all queued messages. Method will not return until all messages
     * sent. (or some failure occur)
     *
     * @see {@link #queue}
     */
    public void sendAllBlocking() {
        CyclicBarrier sendBarrier = new CyclicBarrier(mClients.size() + 1);
        for (ClientHandler client : mClients) {
            sendExecutor.submit(() -> {
                try {
                    sendBarrier.await();
                    client.send();
                    sendBarrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    Log.d(TAG, "waiting barrier interrupted.", e);
                }
            });
        }
        try {
            sendBarrier.await(); // start sending
            sendBarrier.await(); // wait to send
        } catch (Exception e) {
            Log.d(TAG, "waiting barrier interrupted.", e);
        }
    }

    /**
     * Stops receiving messages from clients. Any message which is arrived after
     * a call of this method is discarded by the handler.
     *
     * @see {@link #startReceivingAll}
     */
    public void stopReceivingAll() {
        receiveTimeFlag = false;
    }

    /**
     * Starts receiving messages from clients. The last message which is arrived
     * after a call of this method and before the corresponding call of
     * {@link #stopReceivingAll} is stored as "last valid message".
     *
     * @see {@link #stopReceivingAll}
     */
    public void startReceivingAll() {
        mClients.forEach(server.network.ClientHandler::clearLastValidatedMessage);
        receiveTimeFlag = true;
    }

    /**
     * Returns last valid message which is received from a client.
     *
     * @param clientID ID of the client
     * @return last valid message or <code>null</code> if there is no valid msg
     * @see {@link #defineClient}
     */
    public ReceivedMessage getReceivedMessage(int clientID) {
        return mClients.get(clientID).getLastValidatedMessage();
    }

    /**
     * Returns last valid event which is received from a client.
     *
     * @param clientID ID of the client
     * @return last valid event or <code>null</code> if there is no valid event
     * @see {@link #getReceivedMessage}
     */
    public Event[] getReceivedEvent(int clientID) {
        ReceivedMessage msg = getReceivedMessage(clientID);
        Event[] events = null;
        try {
            /*JsonArray eventArray = ((JsonElement)msg.args[0]).getAsJsonArray();
            events = new Event[eventArray.size()];
            for (int i = 0; i < events.length; i++)
                events[i] = gson.fromJson(eventArray.get(i), Event.class);*/
            events = new Event[msg.args.size()];
            for (int i = 0; i < events.length; i++) {
                events[i] = gson.fromJson(msg.args.get(i), Event.class);
            }
        } catch (Exception e) {
            Log.i(TAG, "Error getting received messages.", e);
        }
        return events;
    }

    @Override
    protected void accept(JsonSocket client) {
        acceptExecutor.submit(() -> {
            try {
                verifyClient(client);
            } catch (Exception e) {
                // if anything was wrong close the client!
                Log.i(TAG, "client rejected", e);
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private boolean verifyClient(JsonSocket client) throws Exception {
        // get the token, timeout is 1000 seconds
        Future<Message> futureMessage
                = acceptExecutor.submit(() -> client.get(Message.class));
        Message token = futureMessage.get(1000, TimeUnit.SECONDS);
        // check the token
        if (token != null && "token".equals(token.name) && token.args != null
                && token.args.length >= 1 && token.args[0] instanceof String) {
            String clientToken = (String) token.args[0];
            int clientID = mTokens.getOrDefault(clientToken, -1);
            if (clientID != -1) {
                mClients.get(clientID).bind(client);
                return true;
            }
        }
        return false;
    }

    /**
     * Blocks caller method until the specified client send a message.
     *
     * @param clientID ID of the client
     * @throws InterruptedException if current thread is interrupted.
     * @see {@link #defineClient}
     */
    public void waitForClientMessage(int clientID) throws InterruptedException {
        mClients.get(clientID).waitForClientMessage();
    }

    /**
     * Blocks caller method at most <code>timeout</code> milliseconds until
     * the specified client send a message.
     *
     * @param clientID ID of the client
     * @throws InterruptedException if current thread is interrupted.
     * @see {@link #defineClient}
     */
    public void waitForClientMessage(int clientID, long timeout) throws InterruptedException {
        mClients.get(clientID).waitForClientMessage(timeout);
    }

    /**
     * Blocks caller method until the specified client is connected to the server.
     *
     * @param clientID ID of the client
     * @throws InterruptedException if current thread is interrupted.
     * @see {@link #defineClient}
     */
    public void waitForClient(int clientID) throws InterruptedException {
        mClients.get(clientID).waitForClient();
    }

    /**
     * Blocks caller method at most <code>timeout</code> milliseconds until
     * the specified client is connected to the server.
     *
     * @param clientID ID of the client
     * @param timeout  timeout in milliseconds
     * @throws InterruptedException if current thread is interrupted.
     * @see {@link #defineClient}
     */
    public void waitForClient(int clientID, long timeout) throws InterruptedException {
        mClients.get(clientID).waitForClient(timeout);
    }

    /**
     * Blocks caller method until all clients are connected to the server.
     *
     * @throws InterruptedException if current thread is interrupted.
     */
    public void waitForAllClients() throws InterruptedException {
        for (ClientHandler client : mClients)
            client.waitForClient();
    }

    /**
     * Blocks caller method at most <code>timeout</code> milliseconds until all
     * clients are connected to the server.
     *
     * @param timeout timeout in milliseconds
     * @throws InterruptedException if current thread is interrupted.
     */
    public void waitForAllClients(long timeout) throws InterruptedException {
        for (ClientHandler client : mClients) {
            long start = System.currentTimeMillis();
            client.waitForClient(timeout);
            long end = System.currentTimeMillis();
            timeout -= end - start;
            if (timeout <= 0)
                return;
        }
    }

    /**
     * Returns number of connected clients.
     *
     * @return num of connected clients.
     */
    public int getNumberOfConnected() {
        int noc = 0;
        for (ClientHandler client : mClients)
            if (client.isConnected())
                noc++;
        return noc;
    }

    /**
     * States that a client is connected or not.
     *
     * @param clientID ID of the client
     * @return true if the client is connected
     * @see {@link #defineClient}
     */
    public boolean isConnected(int clientID) {
        return mClients.get(clientID).isConnected();
    }

    @Override
    public void listen(int port) {
        super.listen(port);
        acceptExecutor = Executors.newCachedThreadPool();
    }

    @Override
    public void terminate() {
        super.terminate();
        acceptExecutor.shutdownNow();
    }

}
