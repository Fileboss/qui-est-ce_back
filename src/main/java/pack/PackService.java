package pack;

import card.Card;
import card.CardRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** Service layer for pack persistence operations. */
@ApplicationScoped
@RequiredArgsConstructor
public class PackService {

    private final PackRepository packRepository;
    private final CardRepository cardRepository;

    /** Creates and persists a new pack with the given name. */
    @Transactional
    public PackDTO createPack(String name) {
        Pack pack = new Pack(name);
        packRepository.persist(pack);
        return toDTO(pack);
    }

    /** Returns all packs as DTOs. */
    public List<PackDTO> getAllPacks() {
        return packRepository.listAll().stream().map(PackService::toDTO).toList();
    }

    /** Returns the pack with the given id, or throws {@link NotFoundException} (HTTP 404). */
    public Pack findById(long packId) {
        Pack pack = packRepository.findById(packId);
        if (pack == null) {
            throw new NotFoundException("Le pack avec l'id " + packId + " n'existe pas.");
        }
        return pack;
    }

    /** Returns the pack with the given id as a DTO, or 404. */
    public PackDTO getPack(long packId) {
        return toDTO(findById(packId));
    }

    /** Renames a pack. */
    @Transactional
    public PackDTO updatePack(long packId, String name) {
        Pack pack = findById(packId);
        pack.setName(name);
        return toDTO(pack);
    }

    /**
     * Deletes the pack and all its cards atomically. Returns the image URLs of the
     * deleted cards so the caller can remove them from S3 after commit.
     */
    @Transactional
    public List<String> deletePackWithCards(long packId) {
        Pack pack = findById(packId);
        List<Card> cards = cardRepository.listByPackId(packId);
        List<String> imageUrls = cards.stream().map(Card::getImageUrl).toList();
        cards.forEach(cardRepository::delete);
        packRepository.delete(pack);
        return imageUrls;
    }

    private static PackDTO toDTO(Pack pack) {
        return new PackDTO(String.valueOf(pack.getId()), pack.getName());
    }
}
