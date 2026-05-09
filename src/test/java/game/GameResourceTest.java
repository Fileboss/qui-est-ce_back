package game;

import card.CardDTO;
import card.CardService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import util.PlayerConflictException;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@QuarkusTest
@TestSecurity(user = "player1", roles = "player")
class GameResourceTest {

    @InjectMock
    GameRegistry gameRegistry;

    @InjectMock
    CardService cardService;

    private static final String FAKE_GAME_ID = "fake-game-id";
    private CardDTO fakeCardDTO;

    @BeforeEach
    void setUp() {
        fakeCardDTO = new CardDTO("1", "Philippe", "http://fake.url/img.png", "1");
    }

    @Test
    void testCreateGame() {
        String packId = "1";
        Mockito.when(cardService.getCardsFromPack(packId)).thenReturn(List.of(fakeCardDTO));
        Mockito.when(gameRegistry.createGame(Mockito.anyList())).thenReturn(new GameDTO("1", GameEngine.GameState.NOT_STARTED.toString(), List.of()));

        given()
            .queryParam("packId", packId)
        .when()
            .post("/game/create")
        .then()
            .statusCode(200)
            .body("gameState", is("NOT_STARTED"))
            .body("gameId", notNullValue());
    }

    @Test
    void testPlayer1JoinGame() {
        Mockito.when(gameRegistry.player1Join(FAKE_GAME_ID, "player1")).thenReturn(fakeCardDTO);

        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player1/join")
        .then()
            .statusCode(200)
            .body("name", is("Philippe"))
            .body("packId", is("1"));

        Mockito.verify(gameRegistry).player1Join(FAKE_GAME_ID, "player1");
    }

    @Test
    void testPlayer2JoinGame() {
        Mockito.when(gameRegistry.player2Join(FAKE_GAME_ID, "player1")).thenReturn(fakeCardDTO);

        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player2/join")
        .then()
            .statusCode(200)
            .body("name", is("Philippe"))
            .body("packId", is("1"));

        Mockito.verify(gameRegistry).player2Join(FAKE_GAME_ID, "player1");
    }

    @Test
    void player1Join_propagatesConflict_returns409() {
        Mockito.when(gameRegistry.player1Join(FAKE_GAME_ID, "player1"))
                .thenThrow(new PlayerConflictException("User already joined this game as player 2"));

        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player1/join")
        .then()
            .statusCode(409)
            .body("Status", is("Failed"))
            .body("Error", is("User already joined this game as player 2"));
    }

    @Test
    void player2Join_propagatesConflict_returns409() {
        Mockito.when(gameRegistry.player2Join(FAKE_GAME_ID, "player1"))
                .thenThrow(new PlayerConflictException("User already joined this game as player 1"));

        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player2/join")
        .then()
            .statusCode(409)
            .body("Status", is("Failed"));
    }

    @Test
    void startGame_returnsSuccess_andOmitsCorrectField() {
        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/start")
        .then()
            .statusCode(200)
            .body("status", is("Success"))
            .body("$", not(hasKey("correct")));
        Mockito.verify(gameRegistry).startGame(FAKE_GAME_ID);
    }

    @Test
    void player1Guess_correctAnswer_returnsCorrectTrue() {
        Mockito.when(gameRegistry.player1Guess(FAKE_GAME_ID, "1")).thenReturn(true);

        given()
            .queryParam("cardId", "1")
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player1/guess")
        .then()
            .statusCode(200)
            .body("status", is("Success"))
            .body("correct", is(true));
    }

    @Test
    void player1Guess_wrongAnswer_returnsCorrectFalse() {
        Mockito.when(gameRegistry.player1Guess(FAKE_GAME_ID, "99")).thenReturn(false);

        given()
            .queryParam("cardId", "99")
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player1/guess")
        .then()
            .statusCode(200)
            .body("correct", is(false));
    }

    @Test
    void resetGame_returnsSuccess_andDelegatesToRegistry() {
        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/reset")
        .then()
            .statusCode(200)
            .body("status", is("Success"));
        Mockito.verify(gameRegistry).resetGame(FAKE_GAME_ID);
    }

    @Test
    void deleteGame_returns204_andCallsRemoveGame() {
        given()
        .when()
            .delete("/game/" + FAKE_GAME_ID)
        .then()
            .statusCode(204);
        Mockito.verify(gameRegistry).removeGame(FAKE_GAME_ID);
    }

    @Test
    void getAll_returnsListOfGames() {
        Mockito.when(gameRegistry.findAll()).thenReturn(List.of(
                new GameDTO("a", GameEngine.GameState.STARTED.toString(), null),
                new GameDTO("b", GameEngine.GameState.NOT_STARTED.toString(), null)
        ));

        given()
        .when()
            .get("/game")
        .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("gameId", contains("a", "b"))
            .body("[0].gameState", equalTo("STARTED"));
    }
}
