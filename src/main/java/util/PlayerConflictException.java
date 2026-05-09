package util;

/** Thrown when a single user attempts to occupy both player slots in a game. Mapped to HTTP 409. */
public class PlayerConflictException extends RuntimeException {
    public PlayerConflictException(String message) {
        super(message);
    }
}
