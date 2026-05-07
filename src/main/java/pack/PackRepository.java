package pack;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/** Panache repository for {@link Pack}. */
@ApplicationScoped
public class PackRepository implements PanacheRepository<Pack> {
}
