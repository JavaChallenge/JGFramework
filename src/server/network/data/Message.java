package server.network.data;

/**
 * Created by rajabzz on 12/27/14.
 */
public class Message {
    public String name;
    public Object[] args;

    public Message(){}

    public Message(String name, Object[] args) {
        this.name = name;
        this.args = args;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}
