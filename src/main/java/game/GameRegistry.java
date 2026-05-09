package game;

import card.CardDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Application-scoped registry holding all active game instances, keyed by UUID. */
@RequiredArgsConstructor
@ApplicationScoped
public class GameRegistry {

    public static final String STATE_CHANGE = "STATE_CHANGE";
    private final ConcurrentHashMap<String, GameEngine> games = new ConcurrentHashMap<>();

    private final Event<GameUpdateEvent> gameUpdateEvent;

    /** Creates a new game from the given card list, stores it, and returns a GameDTO with the full card pack. */
    public GameDTO createGame(List<CardDTO> cards) {
        String id = UUID.randomUUID().toString();
        GameEngine engine = new GameEngine();
        engine.create(cards);
        games.put(id, engine);
        gameUpdateEvent.fireAsync(new GameUpdateEvent(id, "GAME_CREATED", engine.getGameState().toString(), null, null));
        return new GameDTO(id, engine.getGameState().toString(), engine.getCardDTOs());
    }

    public List<GameDTO> findAll() {
        return games.entrySet().stream()
                .map(e -> new GameDTO(e.getKey(), e.getValue().getGameState().toString(), null))
                .toList();
    }

    /** Returns the game for the given id. Throws {@link IllegalArgumentException} if not found. */
    public GameEngine getGame(String gameId) {
        GameEngine engine = games.get(gameId);
        if (engine == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }
        return engine;
    }

    /** Non-throwing lookup, for callers that want to handle the "not found" case themselves. */
    public Optional<GameEngine> findGame(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    /** Removes the game with the given id. No-op if the id does not exist. */
    public void removeGame(String gameId) {
        games.remove(gameId);
        gameUpdateEvent.fireAsync(new GameUpdateEvent(gameId, "DELETED", null, null, null));
    }

    public void resetGame(String gameId) {
        GameEngine engine = getGame(gameId);
        engine.reset();
        gameUpdateEvent.fireAsync(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), null, null));
    }

    public void startGame(String gameId) {
        GameEngine engine = getGame(gameId);
        engine.start();
        gameUpdateEvent.fireAsync(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), null, null));
    }

    public CardDTO player1Join(String gameId, String sub) {
        GameEngine engine = getGame(gameId);
        CardDTO card = engine.player1Join(sub);
        gameUpdateEvent.fireAsync(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), null, engine.getPlayersJoined()));
        return card;
    }

    public CardDTO player2Join(String gameId, String sub) {
        GameEngine engine = getGame(gameId);
        CardDTO card = engine.player2Join(sub);
        gameUpdateEvent.fireAsync(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), null, engine.getPlayersJoined()));
        return card;
    }

    public boolean player1Guess(String gameId, String cardId) {
        GameEngine engine = getGame(gameId);
        boolean correct = engine.player1Guess(cardId);
        gameUpdateEvent.fireAsync(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), correct, null));
        return correct;
    }

    public boolean player2Guess(String gameId, String cardId) {
        GameEngine engine = getGame(gameId);
        boolean correct = engine.player2Guess(cardId);
        gameUpdateEvent.fireAsync(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), correct, null));
        return correct;
    }
}
