package card;

import image.ImageContentTypeSniffer;
import image.ImageService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;
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

    private static final Logger log = Logger.getLogger(CardResource.class);
    private static final long MAX_IMAGE_BYTES = 5_000_000L;

    private final CardService cardService;
    private final ImageService imageService;
    private final ImageContentTypeSniffer sniffer;

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
    @POST
    @Path("/create")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public CardDTO createCard(CardUploadForm form) throws IOException {
        if (form.image == null || form.image.fileName() == null) {
            throw new BadRequestException("Une image est obligatoire");
        }
        if (form.image.size() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Image too large");
        }

        long packId = Long.parseLong(form.packId);
        byte[] fileBytes = Files.readAllBytes(form.image.filePath());

        if (!sniffer.matches(form.image.contentType(), fileBytes)) {
            throw new BadRequestException("Unsupported image type");
        }
        String sniffedType = sniffer.sniff(fileBytes).orElseThrow();

        String imageKey = imageService.uploadImage(fileBytes, sniffedType);
        String imageUrl = imageService.getImageUrl(imageKey);

        try {
            return cardService.createCard(form.name, packId, imageUrl);
        } catch (RuntimeException e) {
            try {
                imageService.deleteImage(imageUrl);
            } catch (RuntimeException compensateEx) {
                log.errorf(compensateEx, "Failed to delete orphaned S3 object %s after DB failure", imageUrl);
            }
            throw e;
        }
    }

    /** Deletes a card and its associated S3 image. Returns 404 if not found. */
    @DELETE
    @Path("/{id}")
    public Response deleteCard(@PathParam("id") long cardId) {
        String imageUrl = cardService.deleteCard(cardId)
                .orElseThrow(() -> new NotFoundException("La carte avec l'id " + cardId + " n'existe pas."));
        imageService.deleteImage(imageUrl);
        return Response.noContent().build();
    }
}
