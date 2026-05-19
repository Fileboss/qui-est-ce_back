package admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.util.List;
import java.util.Objects;

@Path("/admin")
@RolesAllowed("admin")
public class AdminResource {

    @Inject
    KeycloakAdminService keycloakAdminService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "201",
        content = @Content(schema = @Schema(implementation = UserCreateResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request body")
    @APIResponse(responseCode = "409", description = "Username already exists")
    public Response createUser(@Valid UserCreateRequest req) {
        UserCreateResponse created = keycloakAdminService.createUser(req.username(), req.role());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public List<UserSummary> listUsers(
            @QueryParam("first") @DefaultValue("0") @Min(0) int first,
            @QueryParam("max") @DefaultValue("20") @Min(0) @Max(100) int max) {
        return keycloakAdminService.listUsers(first, max);
    }

    @POST
    @Path("/users/{id}/reset-password")
    @Produces(MediaType.APPLICATION_JSON)
    public PasswordResetResponse resetPassword(@PathParam("id") String id) {
        return new PasswordResetResponse(keycloakAdminService.resetPassword(id));
    }

    @DELETE
    @Path("/users/{id}")
    public Response deleteUser(@PathParam("id") String id) {
        if (Objects.equals(jwt.getSubject(), id)) {
            throw new IllegalStateException("Cannot delete your own account");
        }
        keycloakAdminService.deleteUser(id);
        return Response.noContent().build();
    }
}
