package server.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.Consumer;

/**
 * Created by Hadi on 12/17/2014 11:56 PM.
 */
public class NetServerThread extends Thread {

    private final int port;
    private boolean terminateFlag;
    private ServerSocket serverSocket;
    private Consumer<JsonSocket> clientAcceptor;

    public NetServerThread(int port, Consumer<JsonSocket> clientAcceptor) {
        this.port = port;
        this.clientAcceptor = clientAcceptor;
    }

    @Override
    public void run() {
        while (!terminateFlag)
            try {
                runServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    private void runServer() throws IOException {
        if (serverSocket != null && serverSocket.isClosed())
            serverSocket.close();
        serverSocket = new ServerSocket(port);
        while (!terminateFlag)
            clientAcceptor.accept(new JsonSocket(serverSocket.accept()));
    }

    public synchronized void terminate() throws IOException {
        terminateFlag = true;
        serverSocket.close();
    }

}
