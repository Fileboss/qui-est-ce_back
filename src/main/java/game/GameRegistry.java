package game;

import card.CardDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import util.PageResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        gameUpdateEvent.fire(new GameUpdateEvent(id, "GAME_CREATED", engine.getGameState().toString(), null, null));
        return new GameDTO(id, engine.getGameState().toString(), engine.getCardDTOs());
    }

    /** Returns every registered game without pagination. Used by the lobby WebSocket on open. */
    public List<GameDTO> findAll() {
        return games.entrySet().stream()
                .map(e -> new GameDTO(e.getKey(), e.getValue().getGameState().toString(), null))
                .toList();
    }

    /** Returns a page of games ordered by creation time (newest first), with gameId as tiebreaker. */
    public PageResponse<GameDTO> findPage(int first, int max) {
        List<GameDTO> items = games.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, GameEngine> e) -> e.getValue().getCreatedAt())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .skip(first)
                .limit(max)
                .map(e -> new GameDTO(e.getKey(), e.getValue().getGameState().toString(), null))
                .toList();
        return new PageResponse<>(items, first, max, games.size());
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

    /** Removes the game with the given id. Fires DELETED unconditionally — front-end relies on this. */
    public void removeGame(String gameId) {
        GameEngine engine = games.get(gameId);
        if (engine == null) {
            gameUpdateEvent.fire(new GameUpdateEvent(gameId, "DELETED", null, null, null));
            return;
        }
        synchronized (engine) {
            games.remove(gameId);
            gameUpdateEvent.fire(new GameUpdateEvent(gameId, "DELETED", null, null, null));
        }
    }

    public void resetGame(String gameId) {
        GameEngine engine = getGame(gameId);
        synchronized (engine) {
            ensureStillRegistered(gameId);
            engine.reset();
            gameUpdateEvent.fire(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), null, null));
        }
    }

    public void startGame(String gameId) {
        GameEngine engine = getGame(gameId);
        synchronized (engine) {
            ensureStillRegistered(gameId);
            engine.start();
            gameUpdateEvent.fire(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), null, null));
        }
    }

    public CardDTO join(String gameId, String sub) {
        GameEngine engine = getGame(gameId);
        synchronized (engine) {
            ensureStillRegistered(gameId);
            CardDTO card = engine.join(sub);
            gameUpdateEvent.fire(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), null, engine.getPlayersJoined()));
            return card;
        }
    }

    public boolean guess(String gameId, String sub, String cardId) {
        GameEngine engine = getGame(gameId);
        synchronized (engine) {
            ensureStillRegistered(gameId);
            boolean correct = engine.guess(sub, cardId);
            gameUpdateEvent.fire(new GameUpdateEvent(gameId, STATE_CHANGE, engine.getGameState().toString(), correct, null));
            return correct;
        }
    }

    private void ensureStillRegistered(String gameId) {
        if (!games.containsKey(gameId)) {
            throw new IllegalStateException("Game has been deleted: " + gameId);
        }
    }
}
