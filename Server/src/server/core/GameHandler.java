package server.core;

import model.Event;
import network.data.Message;
import server.core.model.ClientInfo;
import server.core.model.Configs;
import server.network.ClientNetwork;
import server.network.UINetwork;
import util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Core controller of the framework, controls the {@link server.core.GameLogic GameLogic}, main loop of the game and
 * does the output controlling operations.
 * <p>
 * This class runs the main running thread of the framework. Class interacts with the clients, UI, and the
 * GameLogic itself.
 * Threads in this class, will gather the clients' events
 * (See also {@link server.network.ClientNetwork ClientNetwork}), send them to the main Game
 * (See also {@link server.core.GameLogic GameLogic})
 * The output will be manipulated and sent to the appropriate controller within a inner module of the class
 * (OutputController).
 * The sequence of the creation and running the operations of this class will be through the call of the following
 * methods.
 * {@link server.core.GameHandler#init() init()}, {@link server.core.GameHandler#start() start()} and then at the
 * moment the external terminal user wants to shut down the games loop (except than waiting for the
 * {@link server.core.GameLogic GameLogic} to flag the end of the game), the
 * {@link server.core.GameHandler#shutdown() shutdown()} method would be called.
 * Note that shutting down the {@link server.core.GameHandler GameHandler} will not immediately stop the threads,
 * actually it will set a shut down request flag in the class, which will closes the thread in the aspect of
 * accepting more inputs, and the terminate the threads as soon as the operation queue got empty.
 * </p>
 */
public class GameHandler {

    private final long GAME_LOGIC_SIMULATE_TIMEOUT;
    private final long GAME_LOGIC_TURN_TIMEOUT;
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
     * The constructor accepts the instances of {@link server.core.GameHandler GameHandler} and
     * {@link server.network.ClientNetwork ClientNetwork} classes. Then sets some configurations of the loops
     * within the "turn_timeout.conf" file ({@see https://github.com/JavaChallenge/JGFramework/wiki wiki}).
     * </p>
     */
    public GameHandler() {
        mClientNetwork = new ClientNetwork();
        mUINetwork = new UINetwork(Configs.getConfigs().ui.token);
        terminalEventsQueue = new LinkedBlockingQueue<>();

        Configs.TimeConfig timeConfig = Configs.getConfigs().turnTimeout;
        GAME_LOGIC_SIMULATE_TIMEOUT = timeConfig.simulateTimeout;
        CLIENT_RESPONSE_TIME = timeConfig.clientResponseTime;
        GAME_LOGIC_TURN_TIMEOUT = timeConfig.turnTimeout;
    }

    /**
     * Initializes the game handler module based on the given game.
     * <p>
     * This method creates needed instance of module {@link server.core.OutputController OutputController} based on
     * configurations located in the "output_handler.conf" file
     * ({@see https://github.com/JavaChallenge/JGFramework/wiki wiki}).
     * Any problem in loading or parsing the configuration JSON files, will result in rising a runtime exception.
     * </p>
     */
    public void init() {
        Configs.OutputHandlerConfig outputHandlerConfig = Configs.getConfigs().outputHandler;
        mOutputController = new OutputController(outputHandlerConfig.sendToUI,
                mUINetwork,
                outputHandlerConfig.timeInterval,
                outputHandlerConfig.sendToFile,
                new File(outputHandlerConfig.filePath),
                outputHandlerConfig.bufferSize);
    }

    /**
     * Setter of the game logic, will set the main game engine of the {@link server.core.GameHandler GameHandler} to
     * the given instance of {@link server.core.GameLogic GameLogic}.
     *
     * @param gameLogic The given instance of the {@link server.core.GameLogic GameLogic} to set in the object.
     */

    public void setGameLogic(GameLogic gameLogic) {
        mGameLogic = gameLogic;
    }

    /**
     * Getter of the {@link server.network.ClientNetwork ClientNetwork} class which is used to contact to the clients
     * through it.
     *
     * @return The {@link server.network.ClientNetwork ClientNetwork} instance used to connect to clients
     */
    public ClientNetwork getClientNetwork() {
        return mClientNetwork;
    }

    /**
     * Getter of the {@link server.network.UINetwork UINetwork} class instance, which is used in order to connect to
     * the UI.
     *
     * @return Instance of {@link server.network.UINetwork UINetwork} class, used to send messages to user interface
     */
    public UINetwork getUINetwork() {
        return mUINetwork;
    }

    /**
     * Getter of the {@link server.core.OutputController} class instance.
     *
     * @return output controller
     */
    public OutputController getOutputController() {
        return mOutputController;
    }

    /**
     * Setter of the {@link server.core.model.ClientInfo ClientInfo} instance stored in the class in order to recognize
     * the clients from each other
     *
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
     * Note that the shutdown requests, will be responded as soon as the current queue of operations got freed.
     * </p>
     */
    public void shutdown() {
        if (mLoop != null)
            mLoop.shutdown();
        if (mOutputController != null)
            mOutputController.shutdown();
    }

    /**
     * Queues an event to be simulated in the next turn of the loop.
     *
     * @param event terminal event
     */
    public void queueEvent(Event event) {
        synchronized (mLoop.terminalEventsQueue) {
            mLoop.terminalEventsQueue.add(event);
        }
    }

    /**
     * In order to give the loop a thread to be ran beside of the main loop.
     * <p>
     * This inner class has a {@link java.util.concurrent.Callable Callable} part, which is wrote down as a
     * runnable code template. This template is composed by the multiple steps in every turn of the game.
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
            clientEvents = new Event[mClientsInfo.length][];
            for (int i = 0; i < clientEvents.length; i++) {
                clientEvents[i] = new Event[0];
            }

            Callable<Void> simulate = () -> {
                mGameLogic.simulateEvents(terminalEvents, environmentEvents, clientEvents);
                mGameLogic.generateOutputs();
                if (mGameLogic.isGameFinished()) {
                    mGameLogic.terminate();
                    Message shutdown = new Message(Message.NAME_SHUTDOWN, new Object[]{});
                    for (int i = 0; i < mClientsInfo.length; i++) {
                        mClientNetwork.queue(i, shutdown);
                    }
                    mLoop.shutdown();
                    mOutputController.shutdown();
                }

                mOutputController.putMessage(mGameLogic.getUIMessage());
                mOutputController.putMessage(mGameLogic.getStatusMessage());

                Message[] output = mGameLogic.getClientMessages();
                for (int i = 0; i < output.length; ++i) {
                    mClientNetwork.queue(i, output[i]);
                }
                mClientNetwork.sendAllBlocking();

                mClientNetwork.startReceivingAll();
                long elapsedTime = System.currentTimeMillis();
                environmentEvents = mGameLogic.makeEnvironmentEvents();
                elapsedTime = System.currentTimeMillis() - elapsedTime;
                if (CLIENT_RESPONSE_TIME - elapsedTime > 0) {
                    try {
                        Thread.sleep(CLIENT_RESPONSE_TIME - elapsedTime);
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

                BlockingQueue<Event> terminalEventsQueue = new LinkedBlockingQueue<>();

                synchronized (terminalEventsQueue) {
                    terminalEvents = terminalEventsQueue.toArray(new Event[terminalEventsQueue.size()]);
                    terminalEventsQueue.clear();
                }

                return null;
            };

            while (!shutdownRequest) {
                long start = System.currentTimeMillis();
                try {
                    simulate.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                long end = System.currentTimeMillis();
                long remaining = GAME_LOGIC_TURN_TIMEOUT - (end - start);
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

            synchronized (this) {
                notifyAll();
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

    public void waitForFinish() throws InterruptedException {
        final Loop loop = mLoop;
        if (loop != null)
            synchronized (loop) {
                loop.wait();
            }
    }

}
