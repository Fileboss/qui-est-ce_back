package pack;

import card.CardDto;
import card.CardService;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Path("/pack")
public class PackResource {
    private final PackService packService;
    private final CardService cardService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PackDto> getAll() {
        return packService.getAllPacks();
    }

    @GET
    @Path("/{id}/cards")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CardDto> getCardsByPack(@PathParam("id") String packId) {
        return cardService.getCardsFromPack(packId);
    }

    @Path("/create")
    @PUT
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    public PackDto createPack(@QueryParam("packName") String packName) {
        return packService.createPack(packName);
    }
}
