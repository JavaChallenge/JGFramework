package server.network;

/**
 * This class implements a network server. A <code>NetServer</code> runs a
 * server on the specified port and waits for the clients to be connected,
 * and then based on the implementation of the <code>accept</code> method,
 * performs some operation for each client,
 * e.g. performs some operation based on the request,
 * returns some information to the requester,
 * or offers a terminal for the client.
 * <p>
 * When the method {@link #listen} is called, a
 * {@link server.network.NetServerThread} is assigned to listen on the specified
 * port. When a client attempts to connect to that port, the {@link #accept}
 * method of the class is called, with a reference to the
 * <code>JsonSocket</code> which is connected to the client. This is the initial
 * point of the connection and one can send initial messages to start
 * communication with the client.
 * <p>
 * It is better to do all operations for a client in a separated thread. So if
 * the client is fake it doesn't affect the behavior of the server.
 *
 * @see server.network.NetServerThread
 * @see server.network.TerminalNetwork
 * @see server.network.ClientNetwork
 * @see server.network.UINetwork
 */
public abstract class NetServer {

    /**
     * The thread which is assigned to listen on the port.
     */
    private NetServerThread listener;

    /**
     * A thread is created and assigned to listen on the specified port.
     * {@link #terminate} must be called between two consecutive calls of this
     * function.
     *
     * @param port      server port
     * @see #terminate
     * @see server.network.NetServerThread
     */
    public synchronized void listen(int port) {
        if (listener != null)
            throw new IllegalStateException("NetServer is currently listening.");
        listener = new NetServerThread(port, this::accept);
        listener.start();
    }

    /**
     * Terminates operations of the current listener. {@link #listen} must be
     * called between two consecutive calls of this function.
     *
     * @see #listen
     * @see server.network.NetServerThread
     */
    public synchronized void terminate() {
        if (listener == null)
            throw new IllegalStateException("NetServer is not started or has been terminated.");
        listener.terminate();
        listener = null;
    }

    /**
     * Returns the state of the server.
     *
     * @return true if listening is terminated.
     */
    public boolean isTerminated() {
        return listener == null;
    }

    /**
     * A method to accept new clients. When a client connects to the port of
     * this <code>NetServer</code>, this method is called, with a reference to
     * a {@link server.network.JsonSocket} which is connected to the client side
     * socket. A new thread may be assigned to handle requests of the client.
     * <p>
     * This method is abstract and so it is not implemented in this class.
     * Each server has a different behavior on accepting new clients, e.g.
     * it may take a token from client or send some information at initial point.
     * To implement a server, one can extend this class and implement this method.
     * <p>
     * Note that this method must take care of everything that could be happen
     * during communications between client and server.
     *
     * @param client    a {@link server.network.JsonSocket} which is connected
     *                  to the client's socket.
     */
    protected abstract void accept(JsonSocket client);

}
