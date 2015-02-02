package server.core;

import server.core.model.Event;
import server.network.TerminalNetwork;
import server.network.data.Command;
import server.network.data.CommandReport;

/**
 * Created by alilotfi on 2/2/15.
 */
public class CommandHandler implements TerminalNetwork.TerminalInterface {

    @Override
    public CommandReport runCommand(Command command) {
        return null;
    }

    @Override
    public void putEvent(Event event) {

    }
}
