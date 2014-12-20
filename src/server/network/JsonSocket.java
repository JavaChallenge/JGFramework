package server.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * This class is a wrapper for java's <code>Socket</code> that uses json strings
 * to transmit objects.
 * <p>
 * A <code>JsonSocket</code> holds a <code>Gson</code> object to convert
 * objects to json string and vice-versa, and a <code>Socket</code> to
 * send and receive json strings.
 * <p>
 * To create a <code>JsonSocket</code>, one can pass an existing socket
 * to the constructor of this class.
 * <p>
 * After a <code>JsonSocket</code> was successfully created, one can send and
 * receive objects with methods <code>send</code> and <code>get</code>.
 * <p>
 * Note that the other endpoint of the communication must use a
 * <code>JsonSocket</code> to send and receive objects correctly.
 * <p>
 * This class is a member of {@link server.network}.
 *
 * @see com.google.gson.Gson
 * @see java.net.Socket
 *
 */
public class JsonSocket {

    /**
     * The <code>Gson</code> object is used to convert between java and json.
     */
    private Gson mGson;
    /**
     * The underlying <code>Socket</code>.
     */
    private Socket mSocket;
    /**
     * Input stream of the socket.
     */
    private DataInputStream mIn;
    /**
     * Output stream of the socket.
     */
    private DataOutputStream mOut;

    /**
     * Initiates new socket with specified host and port and uses
     * that socket to transmit json strings.
     *
     * @param host    the host name.
     * @param port    the port number.
     * @throws IOException if an I/O error occurs when creating the socket or
     *         its input or output stream.
     * @see java.net.Socket#Socket(java.lang.String, int)
     * @see #JsonSocket(java.net.Socket)
     */
    public JsonSocket(String host, int port) throws IOException {
        this(new Socket(host, port));
    }

    /**
     * Uses an existing socket to transmit json strings.
     *
     * @param socket    the socket object.
     * @throws IOException if an I/O error occurs when creating the
     *         input or output stream of the socket.
     */
    public JsonSocket(Socket socket) throws IOException {
        mGson = new Gson();
        mSocket = socket;
        mIn = new DataInputStream(mSocket.getInputStream());
        mOut = new DataOutputStream(mSocket.getOutputStream());
    }

    /**
     * Closes the underlying socket.
     *
     * @throws IOException if an I/O error occurs when closing the socket.
     * @see java.net.Socket#close
     * @see #isClosed
     */
    public void close() throws IOException {
        mSocket.close();
    }

    /**
     * Returns the closed state of the socket.
     *
     * @return true if the socket has been closed
     * @see java.net.Socket#isClosed
     * @see #close
     */
    public boolean isClosed() {
        return mSocket.isClosed();
    }

    /**
     * Converts <code>obj</code> to json string and sends it via the socket's
     * output stream.
     *
     * @param obj    the object to send.
     * @throws IOException if an I/O error occurs, e.g. when the socket is closed.
     * @see com.google.gson.Gson#toJson(java.lang.Object)
     */
    public void send(Object obj) throws IOException {
        String json = mGson.toJson(obj);
        byte buffer[] = json.getBytes("utf-8");
        mOut.writeInt(buffer.length);
        mOut.write(buffer, 0, buffer.length);
    }

    /**
     * Reads a json string from socket's input stream, and converts it to
     * a <code>JsonObject</code>.
     *
     * @return the received <code>JsonObject</code>
     * @throws IOException if an I/O error occurs, e.g. when the socket is closed.
     * @see com.google.gson.Gson#fromJson(String,java.lang.Class)
     * @see #get(java.lang.Class)
     */
    public JsonObject get() throws IOException {
        return get(JsonObject.class);
    }

    /**
     * Reads a json string from socket's input stream, and converts it to
     * a an object of the specified class.
     *
     * @return the received object
     * @throws IOException if an I/O error occurs, e.g. when the socket is closed.
     * @see com.google.gson.Gson#fromJson(String,java.lang.Class)
     * @see #get
     */
    public <T> T get(Class<T> classOfInput) throws IOException {
        int length = mIn.readInt(), total = 0, current;
        byte buffer[] = new byte[length];
        while (total < length) {
            current = mIn.read(buffer, total, length-total);
            total += current;
            if (current == -1)
                throw new IOException("EOF reached.");
        }
        String json = new String(buffer, 0, buffer.length, "utf-8");
        return mGson.fromJson(json, classOfInput);
    }

}
