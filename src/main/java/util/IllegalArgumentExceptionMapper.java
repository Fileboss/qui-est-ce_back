package util;

import jakarta.json.Json;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Maps {@link IllegalArgumentException} (unknown game id) to HTTP 404. */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(IllegalArgumentException exception) {
        var json = Json.createObjectBuilder()
                .add("Status", "Failed")
                .add("Error", exception.getMessage())
                .build();

        return Response.status(Response.Status.NOT_FOUND)
                .entity(json.toString())
                .build();
    }
}
