package card;

import image.ImageService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;

/** REST endpoints for card management. */
@RequiredArgsConstructor
@Path("/card")
@RolesAllowed("admin")
public class CardResource {

    private final CardService cardService;
    private final ImageService imageService;

    /** Multipart form used to upload a card with its image. */
    public static class CardUploadForm {
        @RestForm("name")
        @PartType(MediaType.TEXT_PLAIN)
        public String name;

        @RestForm("packId")
        @PartType(MediaType.TEXT_PLAIN)
        public String packId;

        @RestForm("image")
        @PartType(MediaType.APPLICATION_OCTET_STREAM)
        public FileUpload image;
    }


    /**
     * API to create a card.
     * @param form the Form Data containing Card information and image data.
     * @return a CardDto corresponding to the created card.
     * @throws IOException if an error occurs while reading image data.
     */
    @PUT
    @Path("/create")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public CardDTO createCard(CardUploadForm form) throws IOException {
        if (form.image == null || form.image.fileName() == null) {
            throw new BadRequestException("Une image est obligatoire");
        }

        byte[] fileBytes = Files.readAllBytes(form.image.filePath());
        String imageKey = imageService.uploadImage(fileBytes, form.image.contentType());
        String imageUrl = imageService.getImageUrl(imageKey);

        return cardService.createCard(form.name, Long.parseLong(form.packId), imageUrl);
    }

    /** Deletes a card and its associated S3 image. Returns 404 if not found. */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteCard(@PathParam("id") long cardId) {
        String imageUrl = cardService.deleteCard(cardId)
                .orElseThrow(() -> new NotFoundException("La carte avec l'id " + cardId + " n'existe pas."));
        imageService.deleteImage(imageUrl);
        return Response.noContent().build();
    }
}
