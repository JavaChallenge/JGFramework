package server.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import server.core.model.ClientInfo;
import server.core.model.Event;
import server.network.ClientNetwork;
import server.network.UINetwork;
import server.network.data.Message;

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
 *     Note that shutting down the {@link server.core.GameHandler GameHandler} will not immedietelly stop the threads,
 *     actually it will set a shut down request flag in the class, which will
 * </p>
 */
public class GameHandler {

    private static final String RESOURCE_PATH_OUTPUT_HANDLER = "/resources/game_handler/output_handler.conf";
    private static final String RESOURCE_PATH_TURN_TIMEOUT = "/resources/game_handler/output_handler.conf";
    private final long GAME_LOGIC_SIMULATE_TIMEOUT;
    private final long CLIENT_RESPONSE_TIME;

    private ClientNetwork mClientNetwork;
    private UINetwork mUINetwork;
    private GameLogic mGameLogic;
    private OutputController mOutputController;
    private ClientInfo[] mClientsInfo;

    private Loop mLoop;
    private ArrayList<Event> mEventsQueue;

    public GameHandler(ClientNetwork clientNetwork, UINetwork uiNetwork) {
        mClientNetwork = clientNetwork;
        mUINetwork = uiNetwork;

        Gson gson = new Gson();
        try {
            File file = new File(RESOURCE_PATH_TURN_TIMEOUT);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            TimeConfig timeConfig = gson.fromJson(file.toString(), TimeConfig.class);
            GAME_LOGIC_SIMULATE_TIMEOUT = timeConfig.getClientResponseTime();
            CLIENT_RESPONSE_TIME = timeConfig.getUIResponseTime();
        } catch (FileNotFoundException notFound) {
            throw new RuntimeException("turn_timeout config file not found");
        } catch (JsonParseException e) {
            throw new RuntimeException("turn_time config file does not meet expected syntax");
        }
    }

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

    public void setGameLogic(GameLogic gameLogic) {
        mGameLogic = gameLogic;
    }

    public ClientNetwork getClientNetwork() {
        return mClientNetwork;
    }

    public UINetwork getUINetwork() {
        return mUINetwork;
    }

    public void setClientsInfo(ClientInfo[] clientsInfo) {
        mClientsInfo = clientsInfo;
    }

    public void start() {
        mLoop = new Loop();
        new Thread(mLoop).start();
        new Thread(mOutputController).start();
    }

    public void shutdown() {
        mLoop.shutdown();
        mOutputController.shutdown();
    }

    private class Loop implements Runnable {

        private boolean shutdownRequest = false;

        private Event[] environmentEvents;
        private Event[] terminalEvents;
        private Event[][] clientEvents;

        @Override
        public void run() {
            Callable<Void> simulate = new Callable<Void>() {

                @Override
                public Void call() throws Exception {

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
                    
                    terminalEvents = null;

                    return null;
                }
            };
            RunnableFuture<Void> runnableSimulate = new FutureTask<Void>(simulate);
            ExecutorService service = Executors.newSingleThreadExecutor();

            while (!shutdownRequest) {
                service.execute(runnableSimulate);
                try {
                    runnableSimulate.get(GAME_LOGIC_SIMULATE_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (ExecutionException execution) {
                    throw new RuntimeException("GameLogic execution encountered exception");
                } catch (TimeoutException timeOut) {
                    runnableSimulate.cancel(true);
                } catch (InterruptedException interrupted) {
                    throw new RuntimeException("GameLogic execution interrupted");
                }
            }
            service.shutdown();
        }

        public void shutdown() {
            this.shutdownRequest = true;
        }
    }

    private class OutputHandlerConfig {
        private boolean sendToUI;
        private int timeInterval;
        private boolean sendToFile;
        private String filePath;
        private int bufferSize;

        private File getFile() {
            return new File(filePath);
        }
    }

    /**
     *
     */
    private static class TimeConfig {
        private long mClientResponseTime;
        private long mSimulateTimeout;

        public TimeConfig(long clientResponseTime,long uiResponseTime) {
            mClientResponseTime = clientResponseTime;
            mSimulateTimeout = uiResponseTime;
        }

        /**
         *
         * @return
         */
        public long getClientResponseTime() {
            return mClientResponseTime;
        }

        /**
         *
         * @return
         */
        public long getUIResponseTime() {
            return mSimulateTimeout;
        }
    }
}
