package game;

import card.CardDTO;
import card.CardService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** REST endpoints for managing and playing games. All game-scoped endpoints require a {gameId} path parameter. */
@RequiredArgsConstructor
@Path("/game")
public class GameResource {

    public static final String SUCCESS = "Success";

    private final GameRegistry gameRegistry;
    private final CardService cardService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<GameDTO> getAll() {
        return gameRegistry.findAll();
    }

    /** Creates a new game from the given pack and returns its id and card pack. */
    @POST
    @Path("/create")
    @Produces(MediaType.APPLICATION_JSON)
    public GameDTO createGame(@QueryParam("packId") String packId) {
        List<CardDTO> cards = cardService.getCardsFromPack(packId);
        return gameRegistry.createGame(cards);
    }

    /** Deletes a game regardless of its current state. Idempotent. */
    @DELETE
    @Path("/{gameId}")
    public Response deleteGame(@PathParam("gameId") String gameId) {
        gameRegistry.removeGame(gameId);
        return Response.noContent().build();
    }

    /** Removes a finished game from the registry. */
    @POST
    @Path("/{gameId}/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse resetGame(@PathParam("gameId") String gameId) {
        gameRegistry.resetGame(gameId);
        return new GameStatusResponse(SUCCESS, null);
    }

    /** Returns the card player 1 must guess. Must be called before start. */
    @POST
    @Path("/{gameId}/player1/join")
    @Produces(MediaType.APPLICATION_JSON)
    public CardDTO player1JoinGame(@PathParam("gameId") String gameId) {
        return gameRegistry.getGame(gameId).getPlayer2CardDTOToGuess();
    }

    /** Returns the card player 2 must guess. Must be called before start. */
    @POST
    @Path("/{gameId}/player2/join")
    @Produces(MediaType.APPLICATION_JSON)
    public CardDTO player2JoinGame(@PathParam("gameId") String gameId) {
        return gameRegistry.getGame(gameId).getPlayer1CardDTOToGuess();
    }

    /** Starts the game after both players have joined. */
    @POST
    @Path("/{gameId}/start")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse startGame(@PathParam("gameId") String gameId) {
        gameRegistry.getGame(gameId).start();
        return new GameStatusResponse(SUCCESS, null);
    }

    /** Submits player 1's guess. Returns whether the guess was correct. */
    @POST
    @Path("/{gameId}/player1/guess")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse player1Guess(@PathParam("gameId") String gameId, @QueryParam("cardId") String cardId) {
        boolean isCorrectAnswer = gameRegistry.getGame(gameId).player1Guess(cardId);
        return new GameStatusResponse(SUCCESS, isCorrectAnswer);
    }

    /** Submits player 2's guess. Returns whether the guess was correct. */
    @POST
    @Path("/{gameId}/player2/guess")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse player2Guess(@PathParam("gameId") String gameId, @QueryParam("cardId") String cardId) {
        boolean isCorrectAnswer = gameRegistry.getGame(gameId).player2Guess(cardId);
        return new GameStatusResponse(SUCCESS, isCorrectAnswer);
    }
}
