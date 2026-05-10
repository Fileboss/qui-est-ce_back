package util;

import jakarta.json.Json;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Maps {@link UserConflictException} (duplicate Keycloak username) to HTTP 409. */
@Provider
public class UserConflictExceptionMapper implements ExceptionMapper<UserConflictException> {
    @Override
    public Response toResponse(UserConflictException exception) {
        var json = Json.createObjectBuilder()
                .add("Status", "Failed")
                .add("Error", exception.getMessage())
                .build();

        return Response.status(Response.Status.CONFLICT)
                .entity(json.toString())
                .build();
    }
}
