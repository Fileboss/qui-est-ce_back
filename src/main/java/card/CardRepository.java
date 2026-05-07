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
}
