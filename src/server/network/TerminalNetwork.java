package server.network;

/**
 * Created by Razi on 12/6/2014.
 */
public class TerminalNetwork extends NetServer {

    protected TerminalNetwork(int port) {
        super(port);
    }

    @Override
    protected void accept(JsonSocket client) {
        // handle new clients
    }

}
