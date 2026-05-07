package pack;

import card.CardDTO;
import card.CardService;
import image.ImageService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** REST endpoints for pack and card listing. */
@RequiredArgsConstructor
@Path("/pack")
public class PackResource {
    private final PackService packService;
    private final CardService cardService;
    private final ImageService imageService;

    /** Returns all available packs. */
    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public List<PackDTO> getAll() {
        return packService.getAllPacks();
    }

    /** Returns all cards belonging to the given pack. */
    @GET
    @Authenticated
    @Path("/{id}/cards")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CardDTO> getCardsByPack(@PathParam("id") String packId) {
        return cardService.getCardsFromPack(packId);
    }

    /** Creates a new pack with the given name. */
    @Path("/create")
    @PUT
    @RolesAllowed("admin")
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    public PackDTO createPack(@QueryParam("packName") String packName) {
        return packService.createPack(packName);
    }

    /** Deletes a pack, all its cards, and their S3 images. Returns 404 if pack not found. */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response deletePack(@PathParam("id") long packId) {
        @SuppressWarnings("java:S3252") // Active Record pattern
        Pack pack = Pack.findById(packId);
        if (pack == null) {
            throw new NotFoundException("Le pack avec l'id " + packId + " n'existe pas.");
        }
        List<String> imageUrls = cardService.deleteCardsFromPack(packId);
        pack.delete();
        imageUrls.forEach(imageService::deleteImage);
        return Response.noContent().build();
    }
}
