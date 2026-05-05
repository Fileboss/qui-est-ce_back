package pack;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Service layer for pack persistence operations. */
@ApplicationScoped
public class PackService {

    /** Creates and persists a new pack with the given name. Must be called within a transaction. */
    public PackDTO createPack(String name) {
        Pack pack = new Pack(name);
        pack.persist();
        return new PackDTO(String.valueOf(pack.id), pack.getName());
    }

    /** Returns all packs as DTOs. */
    public List<PackDTO> getAllPacks() {
        @SuppressWarnings("java:S3252") // Active Record pattern
        List<Pack> packList = Pack.listAll();
        return packList.stream().map(pack -> new PackDTO(String.valueOf(pack.id), pack.getName())).toList();
    }

}
