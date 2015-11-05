package network.data;

import com.google.gson.JsonArray;

/**
 * Created by Razi on 2/11/2015.
 */
public class ReceivedMessage {
    public String name;
    public JsonArray args;

    public ReceivedMessage() {
    }

    public ReceivedMessage(String name, JsonArray args) {
        this.name = name;
        this.args = args;
    }
}
