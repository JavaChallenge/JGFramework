package server.core.model;

/**
 * Is a template for information required for creation and assigned to a client of the game.
 * <p>
 * Parts of this class members are required for creating and connecting a client to the
 * {@link server.network.ClientNetwork ClientNetwork}, these parts have to be wrote down to the config file in
 * "Resources" folder ({@see https://github.com/JavaChallenge/JGFramework/wiki wiki}).
 * </p>
 */
public class ClientInfo {

    private String token;
    private String name;
    private int id;

    /**
     * Setter for the ID of the client.
     * <p>
     * ID of every client would be generated inside the {@link server.network.ClientNetwork ClientNetwor} class.
     * Then the assigned unique ID will be set to the client through this method.
     * </p>
     *
     * @param ID Assigned ID by the {@link server.network.ClientNetwork ClientNetwork} class.
     */
    public void setID(int ID) {
        id = ID;
    }

    //TODO DOC
    public void setName(String name) {
        this.name = name;
    }

    //TODO DOC
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Getter of the Token filed of the object.
     * <p>
     * Returns the 32-character token assigned to the client (normally by user) as an String
     * </p>
     *
     * @return String token of the client
     */
    public String getToken() {
        return token;
    }

    //TODO DOC
    public int getID() {
        return id;
    }

    //TODO DOC
    public String getName() {
        return name;
    }
}
