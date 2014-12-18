package server.network;

/**
 * Created by Razi on 12/6/2014.
 */
public class ClientNetwork extends NetServer {

    protected ClientNetwork(int port) {
        super(port);
    }

    @Override
    protected void accept(JsonSocket client) {
        // handle new clients
    }

}
