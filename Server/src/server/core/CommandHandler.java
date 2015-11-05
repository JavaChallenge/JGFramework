package server.core;

import model.Event;
import network.data.Message;
import server.Server;
import server.network.TerminalNetwork;

import java.io.IOException;
import java.util.HashMap;
import java.util.function.UnaryOperator;

/**
 * Created by alilotfi on 2/2/15.
 */
public class CommandHandler implements TerminalNetwork.TerminalInterface {

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
        defineCommand("exit", this::cmdExit);
        defineCommand("waitForFinish", this::waitForFinish);
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
            return new Message(TerminalNetwork.REPORT_NAME, new Object[]{
                    new Object[]{"This command is not defined."}
            });
        }
    }

    public Message cmdStatus(Message cmd) {
        return new Message(TerminalNetwork.REPORT_NAME, new Object[]{
                new Object[]{"Number of connected clients: " + mServer.getGameHandler().getClientNetwork().getNumberOfConnected()}
        });
    }

    public Message cmdNewGame(Message cmd) {
        String args[] = null;
        if (cmd.args != null) {
            args = new String[cmd.args.length];
            for (int i = 0; i < args.length; i++)
                args[i] = (String) cmd.args[i];
        }
        try {
            mServer.newGame(args, 5 * 60 * 1000, 5 * 60 * 1000); // todo: change static timeouts
        } catch (IOException e) {
            e.printStackTrace();
            return new Message(TerminalNetwork.REPORT_NAME, new Object[]{
                    new Object[]{"failed"}
            });
        }
        return new Message(TerminalNetwork.REPORT_NAME, new Object[]{
                new Object[]{"ready"}
        });
    }

    public Message cmdStartGame(Message cmd) {
        mServer.getGameHandler().start();
        return new Message(TerminalNetwork.REPORT_NAME, new Object[]{
                new Object[]{"Game started successfully!"}
        });
    }

    public Message cmdExit(Message cmd) {
        mServer.shutdown();
        System.exit(0);
        return new Message(TerminalNetwork.REPORT_NAME, new Object[]{
                new Object[]{"Game exited successfully!"}
        });
    }

    public Message waitForFinish(Message cmd) {
        try {
            mServer.getGameHandler().waitForFinish();
        } catch (InterruptedException e) {
            throw new RuntimeException("Wait for finish interrupted.");
        }
        return new Message(TerminalNetwork.REPORT_NAME, new Object[]{
                new Object[]{"Game finished!"}
        });
    }

    @Override
    public void putEvent(Event event) {
        mServer.getGameHandler().queueEvent(event);
    }

}
