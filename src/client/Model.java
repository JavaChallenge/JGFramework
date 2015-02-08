package client;

import server.network.data.Message;

/**
 * Model contains data which describes current state of the game.
 */
public class Model {

    private long turnTimeout;
    private long turnStartTime;

    public void handleInitMessage(Message msg) {
        // game developers' todo store data
    }

    public void handleTurnMessage(Message msg) {
        turnStartTime = System.currentTimeMillis();
        // game developers' todo store data
    }

    public long getTurnTimeout() {
        return turnTimeout;
    }

    public long getTurnTimePassed() {
        return System.currentTimeMillis() - turnStartTime;
    }

    public long getTurnRemainingTime() {
        return turnTimeout - getTurnTimePassed();
    }

    public Message getClientTurn() {
        // game developers' todo collect client's events as a single message
        return null;
    }

}
