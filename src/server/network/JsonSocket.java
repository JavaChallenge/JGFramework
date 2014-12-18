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
        String json = mGson.toJson(obj);
        byte buffer[] = json.getBytes("utf-8");
        mOut.writeInt(buffer.length);
        mOut.write(buffer, 0, buffer.length);
    }

    public JsonObject get() throws IOException {
        return get(JsonObject.class);
    }

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
