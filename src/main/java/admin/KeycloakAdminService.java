package admin;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import util.PageResponse;
import util.UserConflictException;

import java.util.List;

@ApplicationScoped
public class KeycloakAdminService {

    private static final String REALM = "qui-est-ce";

    private final Keycloak keycloak;

    @Inject
    public KeycloakAdminService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public UserCreateResponse createUser(String username, String role) {
        RealmResource realmResource = keycloak.realm(REALM);

        var user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);

        String userId;
        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() == 409) {
                throw new UserConflictException("Username already exists: " + username);
            }
            if (response.getStatus() != 201) {
                throw new IllegalStateException("Keycloak user creation failed: HTTP " + response.getStatus());
            }
            String location = response.getHeaderString("Location");
            userId = location.substring(location.lastIndexOf('/') + 1);
        }

        try {
            String generatedPassword = PasswordGenerator.generate();
            setTemporaryPassword(userId, generatedPassword);

            var userRoles = realmResource.users().get(userId).roles().realmLevel();
            var roleRep = userRoles.listAvailable().stream()
                    .filter(r -> r.getName().equals(role))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Realm role missing: " + role));
            userRoles.add(List.of(roleRep));

            return new UserCreateResponse(userId, username, generatedPassword);
        } catch (RuntimeException e) {
            try {
                realmResource.users().get(userId).remove();
            } catch (RuntimeException cleanup) {
                Log.warnf(cleanup, "Failed to roll back orphan Keycloak user %s after create failure", userId);
            }
            throw e;
        }
    }

    public PageResponse<UserSummary> listUsers(int first, int max) {
        RealmResource realmResource = keycloak.realm(REALM);
        List<UserSummary> items = realmResource.users().list(first, max).stream()
                .map(u -> {
                    List<String> roles = realmResource.users().get(u.getId()).roles().realmLevel().listAll().stream()
                            .map(r -> r.getName())
                            .filter(name -> !name.startsWith("default-roles-"))
                            .toList();
                    return new UserSummary(
                            u.getId(),
                            u.getUsername(),
                            Boolean.TRUE.equals(u.isEnabled()),
                            u.getCreatedTimestamp() == null ? 0L : u.getCreatedTimestamp(),
                            roles
                    );
                })
                .toList();
        long total = realmResource.users().count();
        return new PageResponse<>(items, first, max, total);
    }

    public String resetPassword(String userId) {
        String password = PasswordGenerator.generate();
        try {
            setTemporaryPassword(userId, password);
        } catch (WebApplicationException e) {
            if (isNotFound(e)) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            throw e;
        }
        return password;
    }

    public void deleteUser(String userId) {
        try {
            keycloak.realm(REALM).users().get(userId).remove();
        } catch (WebApplicationException e) {
            if (isNotFound(e)) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            throw e;
        }
    }

    private static boolean isNotFound(WebApplicationException e) {
        var response = e.getResponse();
        return response != null && response.getStatus() == 404;
    }

    private void setTemporaryPassword(String userId, String password) {
        var cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(true);
        keycloak.realm(REALM).users().get(userId).resetPassword(cred);
    }
}
