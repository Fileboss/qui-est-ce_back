package game;

import card.CardDTO;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Random;

@NoArgsConstructor
@ApplicationScoped
public class GameEngine {

    private final Random random = new Random();

    private enum GameState {
        NOT_STARTED,
        PREPARING,
        STARTED,
        PLAYER_1_WINS,
        PLAYER_2_WINS
    }

    @Getter
    private GameState gameState = GameState.NOT_STARTED;
    @Getter
    private List<CardDTO> cardDTOs;

    private CardDTO player1CardDTOToGuess;

    private CardDTO player2CardDTOToGuess;

    public synchronized void create(List<CardDTO> cardDTOs) {
        if (gameState != GameState.NOT_STARTED) {
            throw new IllegalStateException("Can only create a game if not started");
        }
        if (cardDTOs.isEmpty()) {
            throw new IllegalStateException("A game cannot be started with an empty card pack");
        }
        this.cardDTOs = cardDTOs;
        this.player1CardDTOToGuess = this.cardDTOs.get(random.nextInt(cardDTOs.size()));
        this.player2CardDTOToGuess = this.cardDTOs.get(random.nextInt(cardDTOs.size()));
        this.gameState = GameState.PREPARING;
    }

    public CardDTO getPlayer1CardDTOToGuess() {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only start a game which is being prepared lol");
        }
        return player1CardDTOToGuess;
    }

    public CardDTO getPlayer2CardDTOToGuess() {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only start a game which is being prepared");
        }
        return player2CardDTOToGuess;
    }

    public synchronized void start() {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only start a game which is being prepared");
        }
        gameState = GameState.STARTED;
    }

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

    public synchronized void reset() {
        if (!(gameState == GameState.PLAYER_1_WINS || gameState == GameState.PLAYER_2_WINS)) {
            throw new IllegalStateException("Can only reset a game if it is over");
        }
        cardDTOs = null;
        player1CardDTOToGuess = null;
        player2CardDTOToGuess = null;
        gameState = GameState.NOT_STARTED;
    }

}
