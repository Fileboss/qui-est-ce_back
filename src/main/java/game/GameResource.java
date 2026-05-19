package game;

import card.CardDTO;
import card.CardService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import util.PageResponse;

import java.util.List;

/** REST endpoints for managing and playing games. All game-scoped endpoints require a {gameId} path parameter. */
@RequiredArgsConstructor
@Path("/game")
public class GameResource {

    public static final String SUCCESS = "Success";

    private final GameRegistry gameRegistry;
    private final CardService cardService;
    private final SecurityIdentity identity;

    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public PageResponse<GameDTO> getAll(
            @QueryParam("first") @DefaultValue("0") @Min(0) int first,
            @QueryParam("max") @DefaultValue("20") @Min(0) @Max(100) int max) {
        return gameRegistry.findPage(first, max);
    }

    /** Creates a new game from the given pack and returns its id and card pack. */
    @POST
    @Path("/create")
    @RolesAllowed("player")
    @Produces(MediaType.APPLICATION_JSON)
    public GameDTO createGame(@QueryParam("packId") String packId) {
        List<CardDTO> cards = cardService.getCardsFromPack(packId);
        return gameRegistry.createGame(cards);
    }

    /** Deletes a game regardless of its current state. Idempotent. */
    @DELETE
    @Path("/{gameId}")
    @RolesAllowed("player")
    public Response deleteGame(@PathParam("gameId") String gameId) {
        gameRegistry.removeGame(gameId);
        return Response.noContent().build();
    }

    /** Removes a finished game from the registry. */
    @POST
    @Path("/{gameId}/reset")
    @RolesAllowed("player")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse resetGame(@PathParam("gameId") String gameId) {
        gameRegistry.resetGame(gameId);
        return new GameStatusResponse(SUCCESS, null);
    }

    /** Assigns the caller to the next free player slot and returns the card they must guess. */
    @POST
    @Path("/{gameId}/join")
    @RolesAllowed("player")
    @Produces(MediaType.APPLICATION_JSON)
    public CardDTO joinGame(@PathParam("gameId") String gameId) {
        return gameRegistry.join(gameId, identity.getPrincipal().getName());
    }

    /** Starts the game after both players have joined. */
    @POST
    @Path("/{gameId}/start")
    @RolesAllowed("player")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse startGame(@PathParam("gameId") String gameId) {
        gameRegistry.startGame(gameId);
        return new GameStatusResponse(SUCCESS, null);
    }

    /** Submits the caller's guess. Returns whether the guess was correct. */
    @POST
    @Path("/{gameId}/guess")
    @RolesAllowed("player")
    @Produces(MediaType.APPLICATION_JSON)
    public GameStatusResponse guess(@PathParam("gameId") String gameId, @QueryParam("cardId") String cardId) {
        boolean correct = gameRegistry.guess(gameId, identity.getPrincipal().getName(), cardId);
        return new GameStatusResponse(SUCCESS, correct);
    }
}
