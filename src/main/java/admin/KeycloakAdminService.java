package admin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import util.UserConflictException;

import java.util.List;

@ApplicationScoped
public class KeycloakAdminService {

    private static final String REALM = "qui-est-ce";

    @Inject
    Keycloak keycloak;

    public void createUser(String username, String password, String role) {
        var realmResource = keycloak.realm(REALM);

        var user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);

        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() == 409) {
                throw new UserConflictException("Username already exists: " + username);
            }
            if (response.getStatus() != 201) {
                throw new IllegalStateException("Keycloak user creation failed: HTTP " + response.getStatus());
            }

            String location = response.getHeaderString("Location");
            String userId = location.substring(location.lastIndexOf('/') + 1);

            var cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setValue(password);
            cred.setTemporary(false);
            realmResource.users().get(userId).resetPassword(cred);

            var userRoles = realmResource.users().get(userId).roles().realmLevel();
            var roleRep = userRoles.listAvailable().stream()
                    .filter(r -> r.getName().equals(role))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + role));
            userRoles.add(List.of(roleRep));
        }
    }
}
