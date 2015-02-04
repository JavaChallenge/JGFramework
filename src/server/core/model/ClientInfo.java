package server.core.model;

import java.util.UUID;

/**
 * Created by alilotfi on 2/2/15.
 */
public class ClientInfo {

    private String mToken;
    private String mName;
    private int mID;

    public void setID(int ID) {
        mID = ID;
    }

    public String getToken() {
        return mToken;
    }
}
