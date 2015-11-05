package server.core;

import java.io.IOException;

/**
 * Factory class is based on factory design pattern, and returns preferred subclass of the
 * {@link server.core.GameLogic GameLogic} class which represents the main game logic engine of the game.
 * <p>
 * The user must fill in this method to return the preferred subclass of the
 * {@link server.core.GameLogic GameLogic} in order to be used in the
 * {@link server.core.GameHandler GameHandler} class
 * </p>
 */
public interface Factory {

    /**
     * The user fills in this abstract method to return the preferred subclass of the game logic.
     *
     * @return The preferred child of the {@link server.core.GameLogic GameLogic} class
     */
    public GameLogic getGameLogic(String[] options) throws IOException;
}
