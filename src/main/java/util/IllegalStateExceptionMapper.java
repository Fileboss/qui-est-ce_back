package util;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.json.Json;

@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {
    @Override
    public Response toResponse(IllegalStateException exception) {
        var json = Json.createObjectBuilder()
                .add("Status", "Failed")
                .add("Error", exception.getMessage())
                .build();

        return Response.status(Response.Status.BAD_REQUEST) // 400 est souvent mieux que 500 pour une erreur de logique
                .entity(json.toString())
                .build();
    }
}