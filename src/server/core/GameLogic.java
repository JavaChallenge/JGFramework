package server.core;

import server.core.model.ClientInfo;
import server.core.model.Event;
import server.network.data.Message;

import java.util.HashMap;
import java.util.UUID;

import java.util.HashMap;

/**
 * The abstract class representing the main game logic of the user's game.
 * <p>
 *     This class will be the simulator engine of the game.
 * </p>
 */
public interface GameLogic {

    //TODO: Saeed Rajab, change these... common baby...
    /**
     * This method must send initial and necessary values to UI and clients.
     * @return A hashmap that has <code>Token</code> as <strong>key</strong> and a <code>Message</code> as <strong>value</strong>.
     * <code>Token</code> is used for specifying if the <code>Message</code> is for UI or Client.
     */
    public void init();

    /**
     *
     * @return
     */
    public Message getUIInitialMessage();

    /**
     *
     * @return
     */
    public Message[] getClientInitialMessages();

    /**
     *
     * @return
     */
    public ClientInfo[] getClientInfo();

    /**
     * Simulate events based on the current turn event and calculate the changes in game.
     * @param terminalEvent Events that user enters in terminal.
     * @param environmentEvent Events that is related to environment. Suppose we want to develop a strategic game.
     *                         Increasing/Decreasing a specific resource in map is an environment event.
     * @param clientsEvent Events that is related to client e.g. moving the player.
     */
    public void simulateEvents(Event[] terminalEvent, Event[] environmentEvent, Event[][] clientsEvent);

    /**
     * This method generates the output based on the changes that were calculated in
     * {@link #simulateEvents(server.core.model.Event[], server.core.model.Event[], java.util.HashMap)}.
     * @return A hashmap that has <code>Token</code> as <strong>key</strong> and a <code>Message</code> as <strong>value</strong>.
     * <code>Token</code> is used for specifying if the <code>Message</code> is for UI or Client.
     */
    public void generateOutputs();

    public Message getUIMessage();

    public Message[] getClientMessages();

    /**
     * This method is used for making the environment events.
     * @return An array that is environment events.
     */
    public Event[] makeEnvironmentEvents();
    
    public boolean isGameFinished();
}
