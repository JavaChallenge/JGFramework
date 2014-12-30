package server.network.data;

import com.google.gson.JsonObject;

/**
 * Created by rajabzz on 12/22/14.
 */
public class Command {
    public static final String COMMAND = "command";

    public static final int COMMAND_TYPE_HELP = 0;
    public static final int COMMAND_TYPE_STATUS = 1;
    public static final int COMMAND_TYPE_NEW_GAME = 2;
    public static final int COMMAND_TYPE_LOAD_GAME = 3;
    public static final int COMMAND_TYPE_PAUSE_GAME = 4;
    public static final int COMMAND_TYPE_RESUME_GAME = 5;
    public static final int COMMAND_TYPE_END_GAME = 6;
    public static final int COMMAND_TYPE_CONNECT = 7;
    public static final int COMMAND_TYPE_DISCONNECT = 8;
    public static final int COMMAND_TYPE_EXIT = 9;


    public String cmdType;
    public String[] args;

}
