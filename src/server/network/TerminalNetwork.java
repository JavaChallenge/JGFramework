package server.network;

import com.google.gson.JsonObject;
import server.core.model.Event;
import server.network.data.Command;
import server.network.data.CommandReport;
import server.network.data.Message;

import java.io.IOException;

/**
 * Created by Razi on 12/6/2014.
 */
public class TerminalNetwork extends NetServer {

    private String token;

    public interface TerminalInterface {
        void putEvent(Event event);
        CommandReport runCommand(Command command);
    }

    private TerminalInterface handler;

    @Override
    protected void accept(JsonSocket client) {
        new TerminalNetworkThread(client).start();
    }

    public TerminalNetwork(String token) {
        this.token = token;
    }

    public void setHandler(TerminalInterface handler) {
        this.handler = handler;
    }

    class TerminalNetworkThread extends Thread  {

        private JsonSocket client;

        private boolean userHasCommand = true;

        public TerminalNetworkThread (JsonSocket client) {

            this.client = client;

        }
        @Override
        public void run() {
            try {
                Message msg = client.get(Message.class);

                if (!msg.name.equals(token))
                    return;
                if (!msg.args[0].equals(token))
                    return;

                // now the user is a valid one
                while (userHasCommand) {
                    try {
                        getInput();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void getInput() throws IOException {
            Message msg = client.get(Message.class);
            if (msg.name.equals(Command.COMMAND)) {
                handleCommand(msg);
            } else if (msg.name.equals(Event.EVENT)) {
                handleEvent(msg);
            } else {
                client.send("Your input is invalid!\nPlease don't hack me :)");
            }
        }

        private void handleEvent(Message msg) {
            handler.putEvent((Event)msg.args[0]);
        }

        public void handleCommand(Message msg) throws IOException {
            Command cmd = new Command();
            String commandType = (String)msg.args[0];

            // TODO use reflection for this part
            if (commandType.equals("newGame")) {
                cmd.cmdType = Command.COMMAND_TYPE_NEW_GAME;
            } else if (commandType.equals("help")) {
                cmd.cmdType = Command.COMMAND_TYPE_HELP;
            } else if (commandType.equals("status")) {
                cmd.cmdType = Command.COMMAND_TYPE_STATUS;
            } else if (commandType.equals("resumeGame")) {
                cmd.cmdType = Command.COMMAND_TYPE_RESUME_GAME;
            } else if (commandType.equals("loadGame")) {
                cmd.cmdType = Command.COMMAND_TYPE_LOAD_GAME;
            } else if (commandType.equals("pauseGame")) {
                cmd.cmdType = Command.COMMAND_TYPE_PAUSE_GAME;
            } else if (commandType.equals("endGame")) {
                cmd.cmdType = Command.COMMAND_TYPE_END_GAME;
            } else if (commandType.equals("connect")) {
                cmd.cmdType = Command.COMMAND_TYPE_CONNECT;
            } else if (commandType.equals("disconnect")) {
                cmd.cmdType = Command.COMMAND_TYPE_DISCONNECT;
            } else if (commandType.equals("exit")) {
                cmd.cmdType = Command.COMMAND_TYPE_EXIT;
            }

            cmd.arg = (JsonObject)msg.args[1];

            CommandReport cmdReport = handler.runCommand(cmd);

            client.send(new Message(CommandReport.REPORT, new Object[] {cmdReport.args}));
        }
    }

}
