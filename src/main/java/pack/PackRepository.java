package pack;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Panache repository for {@link Pack}. */
@ApplicationScoped
public class PackRepository implements PanacheRepository<Pack> {

    /** Returns a page of packs ordered by creation time (newest first), with {@code id} as tiebreaker. */
    public List<Pack> findPage(int first, int max) {
        return find("ORDER BY createdAt DESC, id DESC").range(first, first + max - 1).list();
    }
}
