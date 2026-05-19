package pack;

import card.CardDTO;
import card.CardService;
import image.ImageService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import util.PageResponse;

import java.util.List;

/** REST endpoints for pack and card listing. */
@RequiredArgsConstructor
@Path("/pack")
public class PackResource {
    private final PackService packService;
    private final CardService cardService;
    private final ImageService imageService;

    /** Returns a page of available packs, newest first. */
    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public PageResponse<PackDTO> getAll(
            @QueryParam("first") @DefaultValue("0") @Min(0) int first,
            @QueryParam("max") @DefaultValue("20") @Min(0) @Max(100) int max) {
        return packService.getPacksPage(first, max);
    }

    /** Returns a single pack by id. Returns 404 if not found. */
    @GET
    @Authenticated
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public PackDTO getOne(@PathParam("id") long packId) {
        return packService.getPack(packId);
    }

    /** Returns a page of cards belonging to the given pack, newest first. */
    @GET
    @Authenticated
    @Path("/{id}/cards")
    @Produces(MediaType.APPLICATION_JSON)
    public PageResponse<CardDTO> getCardsByPack(
            @PathParam("id") String packId,
            @QueryParam("first") @DefaultValue("0") @Min(0) int first,
            @QueryParam("max") @DefaultValue("20") @Min(0) @Max(100) int max) {
        return cardService.getCardsFromPackPage(packId, first, max);
    }

    /** Creates a new pack with the given name. */
    @Path("/create")
    @POST
    @RolesAllowed("admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PackDTO createPack(@Valid PackRequest req) {
        return packService.createPack(req.packName());
    }

    /** Renames a pack. Returns 404 if not found. */
    @PATCH
    @Path("/{id}")
    @RolesAllowed("admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PackDTO updatePack(@PathParam("id") long packId, @Valid PackRequest req) {
        return packService.updatePack(packId, req.packName());
    }

    /** Deletes a pack, all its cards, and their S3 images. Returns 404 if pack not found. */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public Response deletePack(@PathParam("id") long packId) {
        List<String> imageUrls = packService.deletePackWithCards(packId);
        imageUrls.forEach(imageService::deleteImage);
        return Response.noContent().build();
    }
}
