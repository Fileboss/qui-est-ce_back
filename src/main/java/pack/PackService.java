package pack;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** Service layer for pack persistence operations. */
@ApplicationScoped
@RequiredArgsConstructor
public class PackService {

    private final PackRepository packRepository;

    /** Creates and persists a new pack with the given name. Must be called within a transaction. */
    public PackDTO createPack(String name) {
        Pack pack = new Pack(name);
        packRepository.persist(pack);
        return toDTO(pack);
    }

    /** Returns all packs as DTOs. */
    public List<PackDTO> getAllPacks() {
        return packRepository.listAll().stream().map(PackService::toDTO).toList();
    }

    /** Returns the pack with the given id, or throws {@link NotFoundException} (HTTP 404). */
    public Pack findById(long packId) {
        Pack pack = packRepository.findById(packId);
        if (pack == null) {
            throw new NotFoundException("Le pack avec l'id " + packId + " n'existe pas.");
        }
        return pack;
    }

    /** Returns the pack with the given id as a DTO, or 404. */
    public PackDTO getPack(long packId) {
        return toDTO(findById(packId));
    }

    /** Renames a pack. Must be called within a transaction. */
    public PackDTO updatePack(long packId, String name) {
        Pack pack = findById(packId);
        pack.setName(name);
        return toDTO(pack);
    }

    /** Deletes a pack. Must be called within a transaction. Returns 404 if not found. */
    public void deletePack(long packId) {
        packRepository.delete(findById(packId));
    }

    private static PackDTO toDTO(Pack pack) {
        return new PackDTO(String.valueOf(pack.getId()), pack.getName());
    }
}
