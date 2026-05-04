package pack;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity representing a named collection of cards. Uses the Panache Active Record pattern. */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Pack extends PanacheEntity {
    private String name;
}
