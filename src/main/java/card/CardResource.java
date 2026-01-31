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

    @PUT
    @Path("/create")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public CardDto createCard(CardUploadForm form) throws IOException {
// 1. Validation basique
        if (form.image == null || form.image.fileName() == null) {
            throw new BadRequestException("Une image est obligatoire");
        }

        // 2. Upload de l'image vers MinIO
        byte[] fileBytes = Files.readAllBytes(form.image.filePath());
        String imageKey = imageService.uploadImage(fileBytes, form.image.contentType());

        long packIdAsLong = Long.parseLong(form.packId);
        Pack packFound = Pack.findById(packIdAsLong);

        if (packFound == null) {
            // C'est ici que tu bloques si l'ID n'existe pas !
            throw new NotFoundException("Le pack avec l'id " + form.packId + " n'existe pas.");
        }

        // 3. Création et sauvegarde en Base (Panache)
        Card card = new Card();
        card.setName(form.name);
        card.setPack(packFound);
        card.setImageUrl(imageService.getImageUrl(imageKey)); // On ne stocke que la clé (ex: "uuid-123"), pas l'URL complète

        cardService.createCard(card);

        // 4. Retourne le DTO avec l'URL complète générée
        return new CardDto(
                String.valueOf(card.id),
                card.getName(),
                imageService.getImageUrl(imageKey),
                String.valueOf(card.getPack().id)
        );
    }
}
