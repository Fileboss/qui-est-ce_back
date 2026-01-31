package card;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class    CardService {

    public static final String PACK_ID = "packId";

    public List<CardDto> getCardsFromPack(String packId) {
        List<Card> cards = Card.list("pack.id", Long.parseLong(packId));
        return cards
                .stream()
                .map(card -> new CardDto(String.valueOf(card.id), card.getName(), card.getImageUrl(), String.valueOf(card.getPack().id)))
                .toList();
    }

    public void createCard(Card card) {
        card.persist();
    }

}
