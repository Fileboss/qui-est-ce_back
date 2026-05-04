package card;


/** Immutable representation of a card returned by the API. */
public record CardDTO(String id, String name, String imageUrl, String packId) {
}
