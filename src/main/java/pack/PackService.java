package pack;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PackService {
    public PackDto createPack(String name) {
        Pack pack = new Pack(name);
        pack.persist();
        return new PackDto(String.valueOf(pack.id), pack.getName());
    }
}
