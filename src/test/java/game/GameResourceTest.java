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

@QuarkusTest
class GameResourceTest {

    @InjectMock
    GameEngine gameEngine;

    @InjectMock
    CardService cardService;

    private CardDTO fakeCardDTO;

    @BeforeEach
    void setUp() {
        fakeCardDTO = new CardDTO("1", "Philippe", "http://fake.url/img.png", "1");
    }

    @Test
    void testCreateGame() {
        // Arrange
        String packId = "1";
        Mockito.when(cardService.getCardsFromPack(packId)).thenReturn(List.of(fakeCardDTO));

        // Act and assert
        given()
            .queryParam("packId", packId)
        .when()
            .post("/game/create")
        .then()
            .statusCode(200)
            .body("status", is("Success"));

        Mockito.verify(gameEngine).create(Mockito.anyList());
    }

    @Test
    void testPlayer1JoinGame() {
        // Arrange
        Mockito.when(gameEngine.getPlayer2CardDTOToGuess()).thenReturn(fakeCardDTO);

        // Act and assert
        given()
        .when()
            .post("/game/player1/join")
        .then()
            .statusCode(200)
            .body("name", is("Philippe"))
            .body("packId", is("1"));
    }

    @Test
    void testPlayer2JoinGame() {
        // Arrange
        Mockito.when(gameEngine.getPlayer1CardDTOToGuess()).thenReturn(fakeCardDTO);

        // Act and assert
        given()
                .when()
                .post("/game/player2/join")
                .then()
                .statusCode(200)
                .body("name", is("Philippe"))
                .body("packId", is("1"));
    }

}