package pack;

import card.CardDTO;
import card.CardService;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** REST endpoints for pack and card listing. */
@RequiredArgsConstructor
@Path("/pack")
public class PackResource {
    private final PackService packService;
    private final CardService cardService;

    /** Returns all available packs. */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PackDTO> getAll() {
        return packService.getAllPacks();
    }

    /** Returns all cards belonging to the given pack. */
    @GET
    @Path("/{id}/cards")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CardDTO> getCardsByPack(@PathParam("id") String packId) {
        return cardService.getCardsFromPack(packId);
    }

    /** Creates a new pack with the given name. */
    @Path("/create")
    @PUT
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    public PackDTO createPack(@QueryParam("packName") String packName) {
        return packService.createPack(packName);
    }
}
