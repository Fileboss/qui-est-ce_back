package admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin")
@RolesAllowed("admin")
public class AdminResource {

    @Inject
    KeycloakAdminService keycloakAdminService;

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(@Valid UserCreateRequest req) {
        keycloakAdminService.createUser(req.username(), req.password(), req.role());
        return Response.status(Response.Status.CREATED).build();
    }
}
