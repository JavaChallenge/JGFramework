package network.data;

/**
 * Created by rajabzz on 12/27/14.
 */
public class Message {

    public static final String NAME_TURN = "turn";
    public static final String NAME_INIT = "init";
    public static final String NAME_STATUS = "status";
    public static final String NAME_SHUTDOWN = "shutdown";
    public static final String NAME_WRONG_TOKEN = "wrong token";

    public String name;
    public Object[] args;

    public Message() {
    }

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
