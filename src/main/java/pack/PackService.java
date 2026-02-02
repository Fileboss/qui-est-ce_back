package pack;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PackService {
    public PackDto createPack(String name) {
        Pack pack = new Pack(name);
        pack.persist();
        return new PackDto(String.valueOf(pack.id), pack.getName());
    }

    public List<PackDto> getAllPacks() {
        @SuppressWarnings("java:S3252") // Active Record pattern
        List<Pack> packList = Pack.listAll();
        return packList.stream().map(pack -> new PackDto(String.valueOf(pack.id), pack.getName())).toList();
    }
}
