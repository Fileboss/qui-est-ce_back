package game;

import card.CardDto;
import card.CardService;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Path("/game")
public class GameResource {

    public static final String SUCCESS = "Success";

    private final GameEngine gameEngine;
    private final CardService cardService;

    @POST
    @Path("/create")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse createGame(@QueryParam("packId") String packId) {
        List<CardDto> cardDtos = cardService.getCardsFromPack(packId);
        gameEngine.create(cardDtos);
        return new GameStatusResponse(SUCCESS, null, null);
    }

    @POST
    @Path("/player1/join")
    @Produces(MediaType.APPLICATION_JSON)
    public CardDto player1JoinGame() {
        return gameEngine.getPlayer2CardDtoToGuess();
    }

    @POST
    @Path("/player2/join")
    @Produces(MediaType.APPLICATION_JSON)
    public CardDto player2JoinGame() {
        return gameEngine.getPlayer1CardDtoToGuess();
    }

    @POST
    @Path("/start")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse startGame() {
        gameEngine.start();
        return new GameStatusResponse(SUCCESS, null, null);
    }

    @POST
    @Path("/player1/guess")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse player1Guess(@QueryParam("cardId") String cardId) {
        boolean isCorrectAnswer = gameEngine.player1Guess(cardId);
        return new GameStatusResponse(SUCCESS, null, isCorrectAnswer);
    }

    @POST
    @Path("/player2/guess")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse player2Guess(@QueryParam("cardId") String cardId) {
        boolean isCorrectAnswer = gameEngine.player2Guess(cardId);
        return new GameStatusResponse(SUCCESS, null, isCorrectAnswer);
    }
}
