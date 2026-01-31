package pack;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Path("/pack")
public class PackResource {
    private final PackService packService;

    @Path("/create")
    @PUT
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    public PackDto createPack(@QueryParam("packName") String packName) {
        return packService.createPack(packName);
    }
}
