package card;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Panache repository for {@link Card}. */
@ApplicationScoped
public class CardRepository implements PanacheRepository<Card> {

    private static final String PACK_ID = "pack.id";

    /** Returns all cards belonging to the given pack. */
    public List<Card> listByPackId(long packId) {
        return list(PACK_ID, packId);
    }

    /** Returns a page of cards for the given pack, ordered by creation time (newest first). */
    public List<Card> findPageByPackId(long packId, int first, int max) {
        return find("pack.id = ?1 ORDER BY createdAt DESC, id DESC", packId)
                .range(first, first + max - 1)
                .list();
    }

    /** Total card count for the given pack. */
    public long countByPackId(long packId) {
        return count(PACK_ID, packId);
    }
}
