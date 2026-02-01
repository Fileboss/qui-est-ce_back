package game;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameStatusResponse(String status, String errorMessage, Boolean winner) {
}
