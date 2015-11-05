package server;

import network.data.Message;
import server.core.CommandHandler;
import server.core.Factory;
import server.core.GameHandler;
import server.core.GameLogic;
import server.core.model.ClientInfo;
import server.core.model.Configs;
import server.network.TerminalNetwork;

import java.io.IOException;

/**
 * Class is the main Server of the game with networks and game logic within.
 * <p>
 * {@link server.Server Server} class is the main runner of the framework activities.
 * Creation of the server class will assign the proper objects with the preferred configurations based on
 * files within the config files.
 * The class creates a terminal with connection through a terminal network.
 * Also sets the {@link server.core.GameLogic GameLogic} subclass instance to the
 * {@link server.core.GameHandler GameHandler} of the framework with the utility
 * {@link server.core.Factory Factory} class.
 * Any mistake in the config files causes rising a runtime exception.
 * </p>
 */
public class Server {

    private static final String CONFIG_PATH = "resources/server/server.conf";

    private Factory mFactory;
    private TerminalNetwork mTerminalNetwork;
    private GameHandler mGameHandler;
    private Configs.TerminalConfig mTerminalConfig;
    private Configs.UIConfig mUIConfig;
    private Configs.ClientConfig mClientConfig;

    private ClientInfo[] mClientsInfo;

    /**
     * Constructor of main server of the framework, which creates and connects server components to the object.
     * <p>
     * This class factory accepts a {@link server.core.Factory Factory} instance in order to set the
     * user created subclass of {@link server.core.GameLogic GameLogic} class to the
     * {@link server.core.GameHandler GameHandler} of the server.
     * The configuration of {@link server.network.TerminalNetwork TerminalNetwork} and
     * {@link server.network.UINetwork UINetwork} are inside a file in resources folder.
     * ({@see https://github.com/JavaChallenge/JGFramework/wiki wiki})
     * Occurring any error during parsing config json files, causes a runtime exception to be thrown.
     * </p>
     *
     * @param factory The factory class, implemented by user.
     */
    public Server(Factory factory) {
        mFactory = factory;

        try {
            Configs.load(CONFIG_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Server config file not found.");
        }

        mTerminalConfig = Configs.getConfigs().terminal;
        mTerminalNetwork = new TerminalNetwork(mTerminalConfig.token);

        mUIConfig = Configs.getConfigs().ui;
        mGameHandler = new GameHandler();
        mGameHandler.init();

        mClientConfig = Configs.getConfigs().client;

        setCommandHandler(new CommandHandler());
    }

    public void setCommandHandler(CommandHandler commandHandler) {
        mTerminalNetwork.setHandler(commandHandler);
        commandHandler.setServer(this);
    }

    public Factory getFactory() {
        return mFactory;
    }

    public TerminalNetwork getTerminalNetwork() {
        return mTerminalNetwork;
    }

    public GameHandler getGameHandler() {
        return mGameHandler;
    }

    /**
     * Starts the server by make it listening and responding to the Terminal.
     */
    public void start() {
        System.out.println("server started");
        mTerminalNetwork.listen(mTerminalConfig.port);
    }

    public void newGame(String[] options, long uiTimeout, long clientTimeout) throws IOException {
        GameLogic gameLogic = mFactory.getGameLogic(options);
        gameLogic.init();
        mGameHandler.setGameLogic(gameLogic);
        mClientsInfo = gameLogic.getClientInfo();
        mGameHandler.setClientsInfo(mClientsInfo);
        for (int i = 0; i < mClientsInfo.length; ++i) {
            int id = mGameHandler.getClientNetwork().defineClient(mClientsInfo[i].getToken());
            if (id != i) {
                throw new RuntimeException("Client ID and client order does not match");
            }
            mClientsInfo[i].setID(id);
        }

        if (Configs.getConfigs().ui.enable) {
            mGameHandler.getUINetwork().listen(mUIConfig.port);
            mGameHandler.getClientNetwork().listen(mClientConfig.port);

            try {
                mGameHandler.getUINetwork().waitForClient(uiTimeout);
            } catch (InterruptedException e) {
                throw new RuntimeException("Waiting for ui clients interrupted");
            }

            try {
                mGameHandler.getClientNetwork().waitForAllClients(clientTimeout);
            } catch (InterruptedException e) {
                throw new RuntimeException("Waiting for clients interrupted");
            }

            Message initialMessage = gameLogic.getUIInitialMessage();
            mGameHandler.getUINetwork().sendBlocking(initialMessage);

            Message[] initialMessages = gameLogic.getClientInitialMessages();
            for (int i = 0; i < initialMessages.length; ++i) {
                mGameHandler.getClientNetwork().queue(i, initialMessages[i]);
            }
            mGameHandler.getClientNetwork().sendAllBlocking();
        } else {
            mGameHandler.getClientNetwork().listen(mClientConfig.port);

            try {
                mGameHandler.getClientNetwork().waitForAllClients(clientTimeout);
            } catch (InterruptedException e) {
                throw new RuntimeException("Waiting for clients interrupted");
            }

            Message[] initialMessages = gameLogic.getClientInitialMessages();
            for (int i = 0; i < initialMessages.length; ++i) {
                mGameHandler.getClientNetwork().queue(i, initialMessages[i]);
            }
            mGameHandler.getClientNetwork().sendAllBlocking();
        }
    }

    /**
     * Terminates operations of the server.
     */
    public void shutdown() {
        mGameHandler.shutdown();
        mTerminalNetwork.terminate();
    }

    /**
     * This class is the corresponding object of the json config file.
     * <p>
     * This class will be created from the terminal config json file using the
     * <a href="https://code.google.com/p/google-gson/">google-gson</a> object.
     * The token is the token of the {@link server.network.TerminalNetwork TerminalNetwork}
     * and the port is the port to connect to the {@link server.network.TerminalNetwork TerminalNetwork}.
     * </p>
     */
    private static class TerminalConfig {
        private String token;
        private int port;

        public TerminalConfig(String token, int port) {
            this.token = token;
            this.port = port;
        }

        /**
         * Getter for the terminal token.
         * <p>
         * This method will check the token for a valid 32-character token and returns it as an
         * {@link java.lang.String String}.
         * In the case of an invalid token, raises a runtime exception.
         * </p>
         *
         * @return 32-character token
         */
        public String getTerminalToken() {
            if (token.matches("(.){32}") && token.length() == 32) {
                return token;
            }
            throw new RuntimeException("Invalid terminal token in config file");
        }

        /**
         * Getter for the terminal port.
         * <p>
         * This method checks the port for a valid number and returns it as an integer.
         * In the case of an invalid port number (out of range numbers), raises a runtime exception.
         * </p>
         *
         * @return Integer port number
         */
        public int getTerminalPort() {
            if (port > 0 && port <= 65535) {
                return port;
            } else {
                throw new RuntimeException("Invalid terminal port number in config file");
            }
        }
    }

    /**
     *
     */
    private static class UIConfig {
        private String token;
        private int port;

        public UIConfig(String token, int port) {
            this.token = token;
            this.port = port;
        }

        /**
         * Getter for the user interface token.
         * <p>
         * This method will check the token for a valid 32-character token and returns it as an
         * {@link java.lang.String String}.
         * In the case of an invalid token, raises a runtime exception.
         * </p>
         *
         * @return 32-character token
         */
        public String getUIToken() {
            if (token.matches("(.){32}") && token.length() == 32) {
                return token;
            }
            throw new RuntimeException("Invalid UI token in config file");
        }

        /**
         * Getter for the ui port.
         * <p>
         * This method checks the port for a valid number and returns it as an integer.
         * In the case of an invalid port number (out of range numbers), raises a runtime exception.
         * </p>
         *
         * @return Integer port number
         */
        public int getUIPort() {
            if (port > 0 && port <= 65535) {
                return port;
            } else {
                throw new RuntimeException("Invalid ui port number in config file");
            }
        }
    }

    /**
     *
     */
    private static class ClientConfig {
        private int port;

        public ClientConfig(int port) {
            this.port = port;
        }

        /**
         * Getter for the client server port.
         * <p>
         * This method checks the port for a valid number and returns it as an integer.
         * In the case of an invalid port number (out of range numbers), raises a runtime exception.
         * </p>
         *
         * @return Integer port number
         */
        public int getClientPort() {
            if (port > 0 && port <= 65535) {
                return port;
            } else {
                throw new RuntimeException("Invalid ui port number in config file");
            }
        }
    }
}
