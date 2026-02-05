package card;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CardService {

    public List<CardDTO> getCardsFromPack(String packId) {
        @SuppressWarnings("java:S3252") // Active Record pattern
        List<Card> cards = Card.list("pack.id", Long.parseLong(packId));
        return cards
                .stream()
                .map(card -> new CardDTO(String.valueOf(card.id), card.getName(), card.getImageUrl(), String.valueOf(card.getPack().id)))
                .toList();
    }

    public void createCard(Card card) {
        card.persist();
    }

}
