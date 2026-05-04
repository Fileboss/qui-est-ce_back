package card;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pack.Pack;

/** JPA entity representing a card belonging to a pack. Uses the Panache Active Record pattern. */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Getter
@Setter
public class Card extends PanacheEntity {
    private String name;
    private String imageUrl;
    @ManyToOne
    private Pack pack;
}
