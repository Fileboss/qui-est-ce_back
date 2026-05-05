package game;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Generic game API response. Null fields are omitted from JSON serialization. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameStatusResponse(String status, Boolean correct) {
}
