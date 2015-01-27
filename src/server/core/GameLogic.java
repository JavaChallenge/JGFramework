package server.core;

import server.core.model.Event;
import server.network.data.Message;

/**
 * The abstract class representing the main game logic of the user's game.
 * <p>
 *     This class will be the simulator engine of the game.
 * </p>
 */
public interface GameLogic {

    public Message[] simulateEvents(Event[] events);


    public Message[] setViews(Message[] messages);
}
