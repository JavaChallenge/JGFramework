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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by Razi on 12/6/2014.
 */
public class GameHandler {

    private static final String RESOURCE_PATH_OUTPUT_HANDLER = "/resources/game_handler/output_handler.conf";
    private static long GAME_LOGIC_SIMULATE_TIMEOUT;
    private static long CLIENT_LISTENING_TIMEOUT;

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
    }

    public void init(long simulateTimeout, long clientTimeout) {
        GAME_LOGIC_SIMULATE_TIMEOUT = simulateTimeout;
        CLIENT_LISTENING_TIMEOUT = clientTimeout;
        OutputHandlerConfig outputHandlerConfig;
        File file = new File(RESOURCE_PATH_OUTPUT_HANDLER);
        Gson gson = new Gson();
        try {
            outputHandlerConfig = gson.fromJson(file.toString(), OutputHandlerConfig.class);
        } catch (JsonParseException e) {
            throw new RuntimeException("Output handler config file does not meet expected syntax");
        }
        mOutputController = new OutputController(outputHandlerConfig.sendToUI ,
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
        private Message[] messages;

        @Override
        public void run() {
            Event[] environmentEvents;
            Event[] terminalEvents;
            Event[][] clientEvents;

            environmentEvents = mGameLogic.makeEnvironmentEvents();
            //FIXME: null, and mClientNetwork
            mClientNetwork.startReceivingAll();
            try {
                wait(CLIENT_LISTENING_TIMEOUT);
            } catch (InterruptedException e) {
                throw new RuntimeException("Waiting for clients interrupted");
            }

            clientEvents  = new Event[mClientsInfo.length][];
            for (int i = 0; i < mClientsInfo.length; ++i) {
                if (mClientNetwork.getReceivedEvent(i) != null) {
                    clientEvents[i] = mClientNetwork.getReceivedEvent(i);
                }
            }

            terminalEvents = null;
            mGameLogic.makeEnvironmentEvents();
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
}
