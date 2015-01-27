package server.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import server.core.model.Event;
import server.network.ClientNetwork;
import server.network.UINetwork;
import server.network.data.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Created by Razi on 12/6/2014.
 */
public class GameHandler {

    private static final String RESOURCE_PATH_OUTPUT_HANDLER = "/resources/game_handler/output_handler.conf";
    private static final int GAME_LOGIC_SIMULATE_TIMEOUT = 500;

    private ClientNetwork clientNetwork;
    private UINetwork mUINetwork;
    private GameLogic mGameLogic;
    private OutputController mOutputController;

    private Loop mLoop;
    private ArrayList<Event> mEventsQueue;

    public GameHandler(ClientNetwork clientNetwork, UINetwork uiNetwork, GameLogic gameLogic) {
        this.mGameLogic = gameLogic;
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
        mOutputController = new OutputController(outputHandlerConfig.sendToUI ,
                                                    mUINetwork,
                                                    outputHandlerConfig.timeInterval,
                                                    outputHandlerConfig.sendToFile,
                                                    outputHandlerConfig.getFile(),
                                                    outputHandlerConfig.bufferSize);

    }

    public void start() {
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
            Callable<Message[]> simulate = new Callable<Message[]>() {
                @Override
                public Message[] call() throws Exception {
                    messages = mGameLogic.simulateEvents((Event[]) mEventsQueue.toArray());
                    return messages = mGameLogic.setViews(messages);
                }
            };
            RunnableFuture<Message[]> runnableSimulate = new FutureTask<Message[]>(simulate);
            ExecutorService service = Executors.newSingleThreadExecutor();

            while (!shutdownRequest) {
                service.execute(runnableSimulate);
                try {
                    messages = runnableSimulate.get(GAME_LOGIC_SIMULATE_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (ExecutionException execution) {
                    throw new RuntimeException("GameLogic execution encountered exception");
                } catch (TimeoutException timeOut) {
                    runnableSimulate.cancel(true);
                } catch (InterruptedException interrupted) {
                    throw new RuntimeException("GameLogic execution interrupted");
                }

                for (Message message : messages) {
                    mOutputController.putMessage(message);
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
