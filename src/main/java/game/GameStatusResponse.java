package game;

public record GameStatusResponse(String status, String errorMessage, Boolean winner) {
}
