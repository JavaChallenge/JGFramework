package server.core;

import server.Server;
import server.core.model.Event;
import server.network.TerminalNetwork;
import server.network.data.Message;

import java.util.HashMap;
import java.util.function.UnaryOperator;

/**
 * Created by alilotfi on 2/2/15.
 */
public class CommandHandler implements TerminalNetwork.TerminalInterface {

    public static final String REPORT_NAME = "report";

    protected Server mServer;
    private HashMap<String, UnaryOperator<Message>> mHandlers = new HashMap<>();

    public CommandHandler() {
        defineCommand("status", this::cmdStatus);
        defineCommand("newGame", this::cmdNewGame);
//        defineCommand("loadGame", this::cmdLoadGame);
        defineCommand("startGame", this::cmdStartGame);
//        defineCommand("pauseGame", this::cmdPauseGame);
//        defineCommand("resumeGame", this::cmdResumeGame);
//        defineCommand("endGame", this::cmdEndGame);
//        defineCommand("exit", this::cmdExit);
    }

    public void defineCommand(String command, UnaryOperator<Message> handler) {
        mHandlers.put(command, handler);
    }

    public void setServer(Server server) {
        mServer = server;
    }

    @Override
    public Message runCommand(Message cmd) {
        if (cmd == null || cmd.name == null)
            return null;
        UnaryOperator<Message> handler = mHandlers.get(cmd.name);
        if (handler != null) {
            return handler.apply(cmd);
        } else {
            return new Message(REPORT_NAME, new Object[]{
                    "This command is not defined."
            });
        }
    }

    public Message cmdStatus(Message cmd) {
        return new Message(REPORT_NAME, new Object[] {
                "Number of connected clients: " + mServer.getGameHandler().getClientNetwork().getNumberOfConnected()
        });
    }

    public Message cmdNewGame(Message cmd) {
        String args[] = null;
        if (cmd.args != null) {
            args = new String[cmd.args.length];
            for (int i = 0; i < args.length; i++)
                args[i] = (String) cmd.args[i];
        }
        mServer.newGame(args, 5*60*1000, 5*60*1000); // todo: change static timeouts
        return new Message(REPORT_NAME, null);
    }

    public Message cmdStartGame(Message cmd) {
        mServer.getGameHandler().start();
        return new Message(REPORT_NAME, new Object[] {
                "Game started successfully!"
        });
    }


    @Override
    public void putEvent(Event event) {
        mServer.getGameHandler().queueEvent(event);
    }

}
