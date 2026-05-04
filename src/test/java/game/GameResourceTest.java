package game;

import card.CardDTO;
import card.CardService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@QuarkusTest
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
        Mockito.when(gameRegistry.createGame(Mockito.anyList())).thenReturn(FAKE_GAME_ID);

        given()
            .queryParam("packId", packId)
        .when()
            .post("/game/create")
        .then()
            .statusCode(200)
            .body("status", is("Success"))
            .body("gameId", notNullValue());
    }

    @Test
    void testPlayer1JoinGame() {
        GameEngine mockEngine = Mockito.mock(GameEngine.class);
        Mockito.when(gameRegistry.getGame(FAKE_GAME_ID)).thenReturn(mockEngine);
        Mockito.when(mockEngine.getPlayer2CardDTOToGuess()).thenReturn(fakeCardDTO);

        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player1/join")
        .then()
            .statusCode(200)
            .body("name", is("Philippe"))
            .body("packId", is("1"));
    }

    @Test
    void testPlayer2JoinGame() {
        GameEngine mockEngine = Mockito.mock(GameEngine.class);
        Mockito.when(gameRegistry.getGame(FAKE_GAME_ID)).thenReturn(mockEngine);
        Mockito.when(mockEngine.getPlayer1CardDTOToGuess()).thenReturn(fakeCardDTO);

        given()
        .when()
            .post("/game/" + FAKE_GAME_ID + "/player2/join")
        .then()
            .statusCode(200)
            .body("name", is("Philippe"))
            .body("packId", is("1"));
    }
}
