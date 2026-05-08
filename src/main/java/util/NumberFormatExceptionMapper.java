package util;

import jakarta.json.Json;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Maps {@link NumberFormatException} (non-numeric id parameters) to HTTP 400. */
@Provider
public class NumberFormatExceptionMapper implements ExceptionMapper<NumberFormatException> {
    @Override
    public Response toResponse(NumberFormatException exception) {
        var json = Json.createObjectBuilder()
                .add("Status", "Failed")
                .add("Error", "Invalid numeric id")
                .build();

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(json.toString())
                .build();
    }
}
