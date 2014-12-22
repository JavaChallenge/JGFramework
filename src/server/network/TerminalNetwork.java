package server.network;

import server.core.model.Event;
import server.network.data.Command;
import server.network.data.CommandReport;

/**
 * Created by Razi on 12/6/2014.
 */
public class TerminalNetwork extends NetServer {

    private int port;
    private String token;

    public interface TerminalInterface {
        void putEvent(Event event);
        CommandReport runCommand(Command command);
    }

    @Override
    protected void accept(JsonSocket client) {
        // handle new clients
    }

}
