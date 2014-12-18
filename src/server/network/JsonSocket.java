package server.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Created by Hadi on 12/17/2014 11:53 PM.
 */
public class JsonSocket {

    private Gson mGson;
    private Socket mSocket;
    private DataInputStream mIn;
    private DataOutputStream mOut;

    public JsonSocket(String host, int port) throws IOException {
        this(new Socket(host, port));
    }

    public JsonSocket(Socket socket) throws IOException {
        mGson = new Gson();
        mSocket = socket;
        mIn = new DataInputStream(mSocket.getInputStream());
        mOut = new DataOutputStream(mSocket.getOutputStream());
    }

    public void close() throws IOException {
        mSocket.close();
    }

    public boolean isClosed() {
        return mSocket.isClosed();
    }

    public void send(Object obj) throws IOException {
        mOut.writeUTF(mGson.toJson(obj));
    }

    public JsonObject get() throws IOException {
        return mGson.fromJson(mIn.readUTF(), JsonObject.class);
    }

    public <T> T get(Class<T> classOfInput) throws IOException {
        return mGson.fromJson(mIn.readUTF(), classOfInput);
    }

}
