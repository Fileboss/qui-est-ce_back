package card;

import image.ImageService;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import pack.Pack;

import java.io.IOException;
import java.nio.file.Files;

@RequiredArgsConstructor
@Path("/card")
public class CardResource {

    private final CardService cardService;
    private final ImageService imageService;

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

        long packIdAsLong = Long.parseLong(form.packId);
        @SuppressWarnings("java:S3252") // Active Record pattern
        Pack packFound = Pack.findById(packIdAsLong);

        if (packFound == null) {
            throw new NotFoundException("Le pack avec l'id " + form.packId + " n'existe pas.");
        }

        Card card = new Card();
        card.setName(form.name);
        card.setPack(packFound);
        card.setImageUrl(imageService.getImageUrl(imageKey));

        cardService.createCard(card);

        return new CardDTO(
                String.valueOf(card.id),
                card.getName(),
                imageService.getImageUrl(imageKey),
                String.valueOf(card.getPack().id)
        );
    }
}
