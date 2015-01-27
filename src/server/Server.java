package server;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import server.core.Factory;
import server.core.GameHandler;
import server.network.TerminalNetwork;

import java.io.File;

/**
 * Class is the main Server of the game with networks and game logic within.
 * <p>
 *     {@link server.Server Server} class is the main runner of the framework activities.
 *     Creation of the server class will assign the proper objects with the preferred configurations based on
 *     files within the config files.
 *     The class creates a terminal with connection through a terminal network.
 *     Also sets the {@link server.core.GameLogic GameLogic} subclass instance to the
 *     {@link server.core.GameHandler GameHandler} of the framework with the utility
 *     {@link server.core.Factory Factory} class.
 *     Any mistake in the config files causes rising a runtime exception.
 *     </p>
 */
public class Server {

    private static final String RESOURCE_PATH_TERMINAL = "/resources/network/terminal.conf";

    private TerminalNetwork mTerminalNetwork;
    private GameHandler mGameHandler;
    TerminalConfig mTerminalConfig;

    /**
     * Constructor of main server of the framework, which creates and connects server components to the object.
     * <p>
     *     This class factory accepts a {@link server.core.Factory Factory} instance in order to set the
     *     user created subclass of {@link server.core.GameLogic GameLogic} class to the
     *     {@link server.core.GameHandler GameHandler} of the server.
     *     The configuration of the {@link server.network.TerminalNetwork TerminalNetwork} is inside a file
     *     in resource folder. ({@see https://github.com/JavaChallenge/JGFramework/wiki wiki})
     *     Occurring any error during parsing the config json file, causes a runtime exception to be thrown.
     * </p>
     * @param factory The factory class, implemented by user, will pass a {@link server.core.GameLogic GameLogic}
     *                object through its {@link server.core.Factory#getGameLogic() getGameLogic()} method.
     */
    public Server (Factory factory) {
        File file = new File(RESOURCE_PATH_TERMINAL);
        Gson gson = new Gson();
        try {
            mTerminalConfig = gson.fromJson(file.toString(), TerminalConfig.class);
        } catch (JsonParseException e) {
            throw new RuntimeException("Terminal config file does not meet expected syntax");
        }
        mTerminalNetwork = new TerminalNetwork(mTerminalConfig.getTerminalToken());
        mGameHandler = new GameHandler();
        mGameHandler.setGameLogic(factory.getGameLogic());
    }

    /**
     * Starts the server by make it listening and responding to the Terminal.
     */
    public void start() {
        mTerminalNetwork.listen(mTerminalConfig.getTerminalPort());
    }

    /**
     * This class is the corresponding object of the json config file.
     * <p>
     *     This class will be created from the terminal config json file using the
     *     <a href="https://code.google.com/p/google-gson/">google-gson</a> object.
     *     The token is the token of the {@link server.network.TerminalNetwork TerminalNetwork}
     *     and the port is the port to connect to the {@link server.network.TerminalNetwork TerminalNetwork}.
     * </p>
     */
    private class TerminalConfig {
        private String token;
        private int port;

        /**
         * Getter for the terminal token.
         * <p>
         *     This method will check the token for a valid 32-character token and returns it as an
         *     {@link java.lang.String String}.
         *     In the case of an invalid token, raises a runtime exception.
         * </p>
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
         *     This method checks the port for a valid number and returns it as an integer.
         *     In the case of an invalid port number (out of range numbers), raises a runtime exception.
         * </p>
         * @return Integer port number
         */
        public int getTerminalPort() {
            if (port > 0 && port <= 65535) {
                return port;
            } else {
                throw new RuntimeException("Invalid port number in config file");
            }
        }
    }
}
