package game;

import card.CardDTO;
import lombok.Getter;
import util.PlayerConflictException;

import java.util.List;
import java.util.Random;

/**
 * Holds the state and logic of a single game instance. Thread-safe: state mutations run under the
 * instance monitor (synchronized methods); the state fields are volatile so unsynchronized
 * getters observe the latest publication. CardDTO is an immutable record, so volatile reference
 * publication is sufficient.
 */
public class GameEngine {

    private final Random random = new Random();

    public enum GameState {
        NOT_STARTED,
        PREPARING,
        STARTED,
        PLAYER_1_WINS,
        PLAYER_2_WINS
    }

    @Getter
    private volatile GameState gameState = GameState.NOT_STARTED;
    @Getter
    private volatile List<CardDTO> cardDTOs;

    private volatile CardDTO player1CardDTOToGuess;
    private volatile CardDTO player2CardDTOToGuess;

    private volatile String player1Sub;
    private volatile String player2Sub;

    /** Initializes the game with the given card pack and randomly assigns target cards to each player. */
    public synchronized void create(List<CardDTO> cardDTOs) {
        if (gameState != GameState.NOT_STARTED) {
            throw new IllegalStateException("Can only create a game if not started");
        }
        if (cardDTOs.isEmpty()) {
            throw new IllegalStateException("A game cannot be started with an empty card pack");
        }
        this.cardDTOs = List.copyOf(cardDTOs);
        // Both players may end up with the same target — intentional; documented and tested.
        this.player1CardDTOToGuess = this.cardDTOs.get(random.nextInt(this.cardDTOs.size()));
        this.player2CardDTOToGuess = this.cardDTOs.get(random.nextInt(this.cardDTOs.size()));
        this.gameState = GameState.PREPARING;
    }

    /**
     * Records the caller as player 1 and returns the card player 1 must guess
     * (cross-assignment: player2CardDTOToGuess; see CLAUDE.md). Idempotent for the same sub.
     * Throws {@link PlayerConflictException} if the sub already occupies the player 2 slot.
     */
    public synchronized CardDTO player1Join(String sub) {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only join a game which is being prepared");
        }
        if (sub.equals(player2Sub)) {
            throw new PlayerConflictException("User already joined this game as player 2");
        }
        player1Sub = sub;
        return player2CardDTOToGuess;
    }

    /**
     * Records the caller as player 2 and returns the card player 2 must guess
     * (cross-assignment: player1CardDTOToGuess; see CLAUDE.md). Idempotent for the same sub.
     * Throws {@link PlayerConflictException} if the sub already occupies the player 1 slot.
     */
    public synchronized CardDTO player2Join(String sub) {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only join a game which is being prepared");
        }
        if (sub.equals(player1Sub)) {
            throw new PlayerConflictException("User already joined this game as player 1");
        }
        player2Sub = sub;
        return player1CardDTOToGuess;
    }

    /** Number of distinct players that have joined (0, 1, or 2). */
    public int getPlayersJoined() {
        int count = 0;
        if (player1Sub != null) count++;
        if (player2Sub != null) count++;
        return count;
    }

    /** Transitions the game from PREPARING to STARTED. Both player slots must be occupied. */
    public synchronized void start() {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only start a game which is being prepared");
        }
        if (player1Sub == null || player2Sub == null) {
            throw new IllegalStateException("Cannot start a game until both players have joined");
        }
        gameState = GameState.STARTED;
    }

    /** Evaluates player 1's guess. Returns true and transitions to PLAYER_1_WINS if correct. */
    public synchronized boolean player1Guess(String cardId) {
        if (gameState != GameState.STARTED) {
            throw new IllegalStateException("Can only guess if the game is started");
        }
        if (cardId.equals(player1CardDTOToGuess.id())) {
            gameState = GameState.PLAYER_1_WINS;
            return true;
        }
        return false;
    }

    /** Evaluates player 2's guess. Returns true and transitions to PLAYER_2_WINS if correct. */
    public synchronized boolean player2Guess(String cardId) {
        if (gameState != GameState.STARTED) {
            throw new IllegalStateException("Can only guess if the game is started");
        }
        if (cardId.equals(player2CardDTOToGuess.id())) {
            gameState = GameState.PLAYER_2_WINS;
            return true;
        }
        return false;
    }

    /** Resets the game to NOT_STARTED. Only callable once a player has won. */
    public synchronized void reset() {
        if (!(gameState == GameState.PLAYER_1_WINS || gameState == GameState.PLAYER_2_WINS)) {
            throw new IllegalStateException("Can only reset a game if it is over");
        }
        cardDTOs = null;
        player1CardDTOToGuess = null;
        player2CardDTOToGuess = null;
        player1Sub = null;
        player2Sub = null;
        gameState = GameState.NOT_STARTED;
    }

}
