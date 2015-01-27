package server.core;

import server.core.model.Event;

/**
 * The abstract class representing the main game logic of the user's game.
 * <p>
 *     This class will be the simulator engine of the game.
 * </p>
 */
public abstract class GameLogic {

    public abstract void sendEvent(Event event);
}
