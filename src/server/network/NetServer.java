package server.network;

import java.io.IOException;

/**
 * Created by Hadi on 12/17/2014 11:57 PM.
 */
public abstract class NetServer {

    private int port;
    private NetServerThread listener;

    protected NetServer(int port) {
        this.port = port;
    }

    public synchronized final void listen() {
        if (listener != null)
            throw new IllegalStateException("Network is currently listening.");
        listener = new NetServerThread(port, this::accept);
        listener.start();
    }

    public synchronized final void terminate() {
        if (listener == null)
            throw new IllegalStateException("Network not started or has been terminated.");
        try {
            listener.terminate();
            listener = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected abstract void accept(JsonSocket client);

}
