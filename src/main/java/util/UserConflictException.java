package util;

/** Thrown when a Keycloak user with the same username already exists. Mapped to HTTP 409. */
public class UserConflictException extends RuntimeException {
    public UserConflictException(String message) {
        super(message);
    }
}
