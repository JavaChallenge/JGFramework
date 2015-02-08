package server.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import server.core.model.ClientInfo;
import server.core.model.Event;
import server.network.ClientNetwork;
import server.network.UINetwork;
import server.network.data.Message;
import util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Core controller of the framework, controls the {@link server.core.GameLogic GameLogic}, main loop of the game and
 * does the output controlling operations.
 * <p>
 *     This class runs the main running thread of the framework. Class interacts with the clients, UI, and the
 *     GameLogic itself.
 *     Threads in this class, will gather the clients' events
 *     (See also {@link server.network.ClientNetwork ClientNetwork}), send them to the main Game
 *     (See also {@link server.core.GameLogic GameLogic})
 *     The output will be manipulated and sent to the appropriate controller within a inner module of the class
 *     (OutputController).
 *     The sequence of the creation and running the operations of this class will be through the call of the following
 *     methods.
 *     {@link server.core.GameHandler#init() init()}, {@link server.core.GameHandler#start() start()} and then at the
 *     moment the external terminal user wants to shut down the games loop (except than waiting for the
 *     {@link server.core.GameLogic GameLogic} to flag the end of the game), the
 *     {@link server.core.GameHandler#shutdown() shutdown()} method would be called.
 *     Note that shutting down the {@link server.core.GameHandler GameHandler} will not immediately stop the threads,
 *     actually it will set a shut down request flag in the class, which will closes the thread in the aspect of
 *     accepting more inputs, and the terminate the threads as soon as the operation queue got empty.
 * </p>
 */
public class GameHandler {

    private static final String RESOURCE_PATH_OUTPUT_HANDLER = "resources/game_handler/output_handler.conf";
    private static final String RESOURCE_PATH_TURN_TIMEOUT = "resources/game_handler/turn_timeout.conf";
    private final long GAME_LOGIC_SIMULATE_TIMEOUT;
    private final long GAME_LOGIC_TURN_TIMEOUT = 1000;
    private final long CLIENT_RESPONSE_TIME;

    private ClientNetwork mClientNetwork;
    private UINetwork mUINetwork;
    private GameLogic mGameLogic;
    private OutputController mOutputController;
    private ClientInfo[] mClientsInfo;

    private Loop mLoop;
    BlockingQueue<Event> terminalEventsQueue;

    /**
     * Constructor of the {@link server.core.GameHandler GameHandler}, connects the handler to the Clients through
     * {@link server.network.ClientNetwork ClientNetwork} and to the UI through
     * {@link server.network.UINetwork UINetwork}.
     * <p>
     *     The constructor accepts the instances of {@link server.core.GameHandler GameHandler} and
     *     {@link server.network.ClientNetwork ClientNetwork} classes. Then sets some configurations of the loops
     *     within the "turn_timeout.conf" file ({@see https://github.com/JavaChallenge/JGFramework/wiki wiki}).
     * </p>
     * @param clientNetwork Network which the game will contact to the clients through
     * @param uiNetwork Network which the game will contact to the UI through
     */
    public GameHandler(ClientNetwork clientNetwork, UINetwork uiNetwork) {
        mClientNetwork = clientNetwork;
        mUINetwork = uiNetwork;
        terminalEventsQueue = new LinkedBlockingQueue<>();

        Gson gson = new Gson();
        try {
            File file = new File(RESOURCE_PATH_TURN_TIMEOUT);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            TimeConfig timeConfig = gson.fromJson(bufferedReader, TimeConfig.class);
            GAME_LOGIC_SIMULATE_TIMEOUT = timeConfig.getClientResponseTime();
            CLIENT_RESPONSE_TIME = timeConfig.getUIResponseTime();
        } catch (FileNotFoundException notFound) {
            throw new RuntimeException("turn_timeout config file not found");
        } catch (JsonParseException e) {
            throw new RuntimeException("turn_time config file does not meet expected syntax");
        }
    }

    /**
     * Initializes the game handler module based on the given game.
     * <p>
     *     This method creates needed instance of module {@link server.core.OutputController OutputController} based on
     *     configurations located in the "output_handler.conf" file
     *     ({@see https://github.com/JavaChallenge/JGFramework/wiki wiki}).
     *     Any problem in loading or parsing the configuration JSON files, will result in rising a runtime exception.
     * </p>
     */
    public void init() {
        OutputHandlerConfig outputHandlerConfig;
        Gson gson = new Gson();
        try {
            File file = new File(RESOURCE_PATH_OUTPUT_HANDLER);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            outputHandlerConfig = gson.fromJson(bufferedReader, OutputHandlerConfig.class);
        } catch (FileNotFoundException notFound) {
            throw new RuntimeException("Output handler config file not found");
        } catch (JsonParseException e) {
            throw new RuntimeException("Output handler config file does not meet expected syntax");
        }
        mOutputController = new OutputController(outputHandlerConfig.sendToUI,
                                                    mUINetwork,
                                                    outputHandlerConfig.timeInterval,
                                                    outputHandlerConfig.sendToFile,
                                                    outputHandlerConfig.getFile(),
                                                    outputHandlerConfig.bufferSize);
    }

    /**
     * Setter of the game logic, will set the main game engine of the {@link server.core.GameHandler GameHandler} to
     * the given instance of {@link server.core.GameLogic GameLogic}.
     * @param gameLogic The given instance of the {@link server.core.GameLogic GameLogic} to set in the object.
     */

    public void setGameLogic(GameLogic gameLogic) {
        mGameLogic = gameLogic;
    }

    /**
     * Getter of the {@link server.network.ClientNetwork ClientNetwork} class which is used to contact to the clients
     * through it.
     * @return The {@link server.network.ClientNetwork ClientNetwork} instance used to connect to clients
     */
    public ClientNetwork getClientNetwork() {
        return mClientNetwork;
    }

    /**
     * Getter of the {@link server.network.UINetwork UINetwork} class instance, which is used in order to connect to
     * the UI.
     * @return Instance of {@link server.network.UINetwork UINetwork} class, used to send messages to user interface
     */
    public UINetwork getUINetwork() {
        return mUINetwork;
    }

    /**
     * Setter of the {@link server.core.model.ClientInfo ClientInfo} instance stored in the class in order to recognize
     * the clients from each other
     * @param clientsInfo Array of information of the clients held in the class
     */
    public void setClientsInfo(ClientInfo[] clientsInfo) {
        mClientsInfo = clientsInfo;
    }

    /**
     * Starts the main game ({@link server.core.GameLogic GameLogic}) loop and the
     * {@link server.core.OutputController OutputController} operations in two new {@link java.lang.Thread Thread}.
     */
    public void start() {
        mLoop = new Loop();
        new Thread(mLoop).start();
        new Thread(mOutputController).start();
    }

    /**
     * Registers a shutdown request into the main loop and {@link server.core.OutputController OutputController} class
     * <p>
     *     Note that the shutdown requests, will be responded as soon as the current queue of operations got freed.
     * </p>
     */
    public void shutdown() {
        mLoop.shutdown();
        mOutputController.shutdown();
    }

    /**
     * Queues an event to be simulated in the next turn of the loop.
     *
     * @param event    terminal event
     */
    public void queueEvent(Event event) {
        synchronized (mLoop.terminalEventsQueue) {
            mLoop.terminalEventsQueue.add(event);
        }
    }

    /**
     * In order to give the loop a thread to be ran beside of the main loop.
     * <p>
     *     This inner class has a {@link java.util.concurrent.Callable Callable} part, which is wrote down as a
     *     runnable code template. This template is composed by the multiple steps in every turn of the game.
     * </p>
     */
    private class Loop implements Runnable {

        private boolean shutdownRequest = false;

        private Event[] environmentEvents;
        private Event[] terminalEvents;
        private Event[][] clientEvents;
        private final ArrayList<Event> terminalEventsQueue = new ArrayList<>();

        /**
         * The run method of the {@link java.lang.Runnable Runnable} interface which will create a
         * {@link java.util.concurrent.Callable Callable} instance and call it in a while until the finish flag if the
         * game had been raised or the shutdown request sent to the class (through
         * {@link server.core.GameHandler.Loop#shutdown() shutdown()} method)
         */
        @Override
        public void run() {
            Callable<Void> simulate = () -> {
                mGameLogic.simulateEvents(terminalEvents, environmentEvents, clientEvents);
                mGameLogic.generateOutputs();
                if (mGameLogic.isGameFinished()) {
                    mLoop.shutdown();
                    mOutputController.shutdown();
                }

                mOutputController.putMessage(mGameLogic.getUIMessage());

                Message[] output = mGameLogic.getClientMessages();
                for (int i = 0 ; i < output.length; ++i) {
                    mClientNetwork.queue(i, output[i]);
                }
                mClientNetwork.sendAllBlocking();

                mClientNetwork.startReceivingAll();
                long elapsedTime = System.currentTimeMillis();
                environmentEvents = mGameLogic.makeEnvironmentEvents();
                elapsedTime = System.currentTimeMillis() - elapsedTime;
                if (CLIENT_RESPONSE_TIME - elapsedTime > 0) {
                    try {
                        wait(CLIENT_RESPONSE_TIME - elapsedTime);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Waiting for clients interrupted");
                    }
                }
                mClientNetwork.stopReceivingAll();

                clientEvents = new Event[mClientsInfo.length][];
                for (int i = 0; i < mClientsInfo.length; ++i) {
                    if (mClientNetwork.getReceivedEvent(i) != null) {
                        clientEvents[i] = mClientNetwork.getReceivedEvent(i);
                    }
                }
                //FIXME: Put a blocking queue for terminal
                BlockingQueue<Event> terminalEventsQueue = new LinkedBlockingQueue<>();

                synchronized (terminalEventsQueue) {
                    terminalEvents = terminalEventsQueue.toArray(new Event[terminalEventsQueue.size()]);
                    terminalEventsQueue.clear();
                }

                return null;
            };
//            RunnableFuture<Void> runnableSimulate = new FutureTask<>(simulate);
//            ExecutorService service = Executors.newSingleThreadExecutor();

            while (!shutdownRequest) {
                long start = System.currentTimeMillis();
                try {
                    simulate.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                long end = System.currentTimeMillis();
                long remaining =  GAME_LOGIC_TURN_TIMEOUT - (end - start);
                if (remaining <= 0) {
                    Log.i("GameHandler", "Simulation timeout passed!");
                } else {
                    try {
                        Thread.sleep(remaining);
                    } catch (InterruptedException e) {
                        Log.i("GameHandler", "Loop interrupted!");
                        break;
                    }
                }
            }
        }

        /**
         * Will set the shutdown request flag in order to finish the main {@link server.core.GameHandler.Loop Loop} at
         * the first possible turn
         */
        public void shutdown() {
            this.shutdownRequest = true;
        }
    }

    /**
     * The template of the "output_handler.conf" JSON file.
     * <p>
     *     This class could be filled by a {@link com.google.gson.Gson Gson} object. The properties of class are
     *     normally accessed through {@link server.core.GameHandler#init() init()} method.
     * </p>
     */
    private class OutputHandlerConfig {
        private boolean sendToUI;
        private int timeInterval;
        private boolean sendToFile;
        private String filePath;
        private int bufferSize;

        public OutputHandlerConfig(boolean sendToUI,
                                   int timeInterval,
                                   boolean sendToFile,
                                   String filePath,
                                   int bufferSize) {
            this.sendToUI = sendToUI;
            this.timeInterval = timeInterval;
            this.sendToFile = sendToFile;
            this.filePath = filePath;
            this.bufferSize = bufferSize;
        }
        private File getFile() {
            return new File(filePath);
        }
    }

    /**
     * The template of the "turn_timeout.conf" JSON file.
     * <p>
     *     This class could be filled by a {@link com.google.gson.Gson Gson} object. The properties of class are
     *     normally accessed through {@link server.core.GameHandler#GameHandler GameHandler Constructor} method.
     * </p>
     */
    private static class TimeConfig {
        private long mClientResponseTime;
        private long mSimulateTimeout;

        public TimeConfig(long clientResponseTime,long uiResponseTime) {
            mClientResponseTime = clientResponseTime;
            mSimulateTimeout = uiResponseTime;
        }

        public long getClientResponseTime() {
            return mClientResponseTime;
        }

        public long getUIResponseTime() {
            return mSimulateTimeout;
        }
    }
}
