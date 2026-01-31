package game;

import card.CardDto;
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
        PLAYER_2_WINS;
    }

    @Getter
    private GameState gameState = GameState.NOT_STARTED;
    @Getter
    private List<CardDto> cardDtos;

    private CardDto player1CardDtoToGuess;

    private CardDto player2CardDtoToGuess;

    public void create(List<CardDto> cardDtos) {
        if (gameState != GameState.NOT_STARTED) {
            throw new IllegalStateException("Can only create a game if not started");
        }
        this.cardDtos = cardDtos;
        this.player1CardDtoToGuess = this.cardDtos.get(random.nextInt(cardDtos.size()));
        this.player2CardDtoToGuess = this.cardDtos.get(random.nextInt(cardDtos.size()));
        this.gameState = GameState.PREPARING;
    }

    public CardDto getPlayer1CardDtoToGuess() {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only start a game which is being prepared lol");
        }
        return player1CardDtoToGuess;
    }

    public CardDto getPlayer2CardDtoToGuess() {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only start a game which is being prepared");
        }
        return player2CardDtoToGuess;
    }

    public void start() {
        if (gameState != GameState.PREPARING) {
            throw new IllegalStateException("Can only start a game which is being prepared");
        }
        gameState = GameState.STARTED;
    }

    public boolean player1Guess(String cardId) {
        if (gameState != GameState.STARTED) {
            throw new IllegalStateException("Can only guess if the game is started");
        }
        if (cardId.equals(player1CardDtoToGuess.id())) {
            gameState = GameState.PLAYER_1_WINS;
            return true;
        }
        return false;
    }

    public boolean player2Guess(String cardId) {
        if (gameState != GameState.STARTED) {
            throw new IllegalStateException("Can only guess if the game is started");
        }
        if (cardId.equals(player2CardDtoToGuess.id())) {
            gameState = GameState.PLAYER_2_WINS;
            return true;
        }
        return false;
    }

}
