package card;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Service layer for card persistence operations. */
@ApplicationScoped
public class CardService {

    /** Returns all cards belonging to the given pack, mapped to DTOs. */
    public List<CardDTO> getCardsFromPack(String packId) {
        @SuppressWarnings("java:S3252") // Active Record pattern
        List<Card> cards = Card.list("pack.id", Long.parseLong(packId));
        return cards
                .stream()
                .map(card -> new CardDTO(String.valueOf(card.id), card.getName(), card.getImageUrl(), String.valueOf(card.getPack().id)))
                .toList();
    }

    /** Persists a new card to the database. Must be called within a transaction. */
    public void createCard(Card card) {
        card.persist();
    }

}
