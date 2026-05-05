package card;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/** Service layer for card persistence operations. */
@ApplicationScoped
public class CardService {

    public static final String PACK_ID = "pack.id";

    /** Returns all cards belonging to the given pack, mapped to DTOs. */
    public List<CardDTO> getCardsFromPack(String packId) {
        @SuppressWarnings("java:S3252") // Active Record pattern
        List<Card> cards = Card.list(PACK_ID, Long.parseLong(packId));
        return cards
                .stream()
                .map(card -> new CardDTO(String.valueOf(card.id), card.getName(), card.getImageUrl(), String.valueOf(card.getPack().id)))
                .toList();
    }

    /** Persists a new card to the database. Must be called within a transaction. */
    public void createCard(Card card) {
        card.persist();
    }

    /**
     * Deletes a card by id and returns its image URL, or empty if not found.
     * Must be called within a transaction.
     */
    public Optional<String> deleteCard(long cardId) {
        @SuppressWarnings("java:S3252") // Active Record pattern
        Card card = Card.findById(cardId);
        if (card == null) return Optional.empty();
        String imageUrl = card.getImageUrl();
        card.delete();
        return Optional.of(imageUrl);
    }

    /**
     * Deletes all cards belonging to the given pack and returns their image URLs.
     * Must be called within a transaction.
     */
    public List<String> deleteCardsFromPack(long packId) {
        @SuppressWarnings("java:S3252") // Active Record pattern
        List<Card> cards = Card.list(PACK_ID, packId);
        List<String> imageUrls = cards.stream().map(Card::getImageUrl).toList();
        cards.forEach(Card::delete);
        return imageUrls;
    }

}
