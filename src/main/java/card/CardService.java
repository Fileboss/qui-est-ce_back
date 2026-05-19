package card;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import pack.Pack;
import pack.PackService;
import util.PageResponse;

import java.util.List;
import java.util.Optional;

/** Service layer for card persistence operations. */
@ApplicationScoped
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final PackService packService;

    /** Returns all cards belonging to the given pack, mapped to DTOs. Used by game creation; not paginated. */
    public List<CardDTO> getCardsFromPack(String packId) {
        return cardRepository.listByPackId(Long.parseLong(packId)).stream()
                .map(CardService::toDTO)
                .toList();
    }

    /** Returns a page of cards belonging to the given pack, newest first. */
    public PageResponse<CardDTO> getCardsFromPackPage(String packId, int first, int max) {
        long pid = Long.parseLong(packId);
        List<CardDTO> items = cardRepository.findPageByPackId(pid, first, max).stream()
                .map(CardService::toDTO)
                .toList();
        return new PageResponse<>(items, first, max, cardRepository.countByPackId(pid));
    }

    /** Persists a new card under the given pack. Returns 404 if pack not found. */
    @Transactional
    public CardDTO createCard(String name, long packId, String imageUrl) {
        Pack pack = packService.findById(packId);
        Card card = new Card();
        card.setName(name);
        card.setPack(pack);
        card.setImageUrl(imageUrl);
        cardRepository.persist(card);
        return toDTO(card);
    }

    /** Deletes a card by id and returns its image URL, or empty if not found. */
    @Transactional
    public Optional<String> deleteCard(long cardId) {
        Card card = cardRepository.findById(cardId);
        if (card == null) return Optional.empty();
        String imageUrl = card.getImageUrl();
        cardRepository.delete(card);
        return Optional.of(imageUrl);
    }

    private static CardDTO toDTO(Card card) {
        return new CardDTO(
                String.valueOf(card.getId()),
                card.getName(),
                card.getImageUrl(),
                String.valueOf(card.getPack().getId())
        );
    }
}
