package server.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import server.core.model.ClientInfo;
import server.core.model.Event;
import server.network.ClientNetwork;
import server.network.UINetwork;
import server.network.data.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Created in order to...
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
        File file = new File(RESOURCE_PATH_TURN_TIMEOUT);
        try {
            TimeConfig timeConfig = gson.fromJson(file.toString(), TimeConfig.class);
            GAME_LOGIC_SIMULATE_TIMEOUT = timeConfig.getClientResponseTime();
            CLIENT_RESPONSE_TIME = timeConfig.getUIResponseTime();
        } catch (JsonParseException e) {
            throw new RuntimeException("Turn time config file does not meet expected syntax");
        }
    }

    public void init() {
        OutputHandlerConfig outputHandlerConfig;
        File file = new File(RESOURCE_PATH_OUTPUT_HANDLER);
        Gson gson = new Gson();
        try {
            outputHandlerConfig = gson.fromJson(file.toString(), OutputHandlerConfig.class);
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
