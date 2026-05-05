package game;

import card.CardDTO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Application-scoped registry holding all active game instances, keyed by UUID. */
@ApplicationScoped
public class GameRegistry {

    private final ConcurrentHashMap<String, GameEngine> games = new ConcurrentHashMap<>();

    /** Creates a new game from the given card list, stores it, and returns its generated UUID. */
    public GameStatusResponse createGame(List<CardDTO> cards) {
        String id = UUID.randomUUID().toString();
        GameEngine engine = new GameEngine();
        engine.create(cards);
        games.put(id, engine);
        return new GameStatusResponse(engine.getGameState().toString(), id, null, false);
    }

    public List<GameStatusResponse> findAll() {
        return games.entrySet().stream().map(e ->
                new GameStatusResponse(e.getValue().getGameState().toString(), e.getKey(), null, false)).toList();
    }

    /** Returns the game for the given id. Throws {@link IllegalArgumentException} if not found. */
    public GameEngine getGame(String gameId) {
        GameEngine engine = games.get(gameId);
        if (engine == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }
        return engine;
    }

    /** Removes the game with the given id. No-op if the id does not exist. */
    public void removeGame(String gameId) {
        games.remove(gameId);
    }

    public void resetGame(String gameId) {
        games.get(gameId).reset();
    }
}
