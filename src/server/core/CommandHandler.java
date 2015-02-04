package server.core;

import server.core.model.Event;
import server.network.TerminalNetwork;
import server.network.data.Message;

/**
 * Created by alilotfi on 2/2/15.
 */
public class CommandHandler implements TerminalNetwork.TerminalInterface {

    @Override
    public Message runCommand(Message command) {
        return null;
    }

    @Override
    public void putEvent(Event event) {

    }
}
