package util;

import jakarta.json.Json;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Maps {@link PlayerConflictException} (same user joining both slots) to HTTP 409. */
@Provider
public class PlayerConflictExceptionMapper implements ExceptionMapper<PlayerConflictException> {
    @Override
    public Response toResponse(PlayerConflictException exception) {
        var json = Json.createObjectBuilder()
                .add("Status", "Failed")
                .add("Error", exception.getMessage())
                .build();

        return Response.status(Response.Status.CONFLICT)
                .entity(json.toString())
                .build();
    }
}
