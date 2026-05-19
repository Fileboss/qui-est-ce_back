package admin;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.keycloak.admin.client.Keycloak;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeycloakAdminServiceIntegrationTest {

    private static final String REALM = "qui-est-ce";

    @Inject
    KeycloakAdminService service;

    @Inject
    Keycloak keycloak;

    private final List<String> createdIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String id : createdIds) {
            try {
                keycloak.realm(REALM).users().get(id).remove();
            } catch (WebApplicationException ignored) {
                // already removed by the test
            }
        }
        createdIds.clear();
    }

    @Test
    void createUser_returnsIdAndGeneratedPassword_andUserHasRoleAndTemporaryCredential() {
        String username = uniqueUsername();

        UserCreateResponse created = service.createUser(username, "player");
        createdIds.add(created.id());

        assertNotNull(created.id());
        assertEquals(username, created.username());
        assertTrue(created.generatedPassword().length() >= 16);

        var userResource = keycloak.realm(REALM).users().get(created.id());
        var rep = userResource.toRepresentation();
        assertEquals(username, rep.getUsername());
        assertTrue(Boolean.TRUE.equals(rep.isEnabled()));

        // The temporary=true flag means the credential carries UPDATE_PASSWORD;
        // Keycloak materializes this into requiredActions on the user.
        assertTrue(rep.getRequiredActions().contains("UPDATE_PASSWORD"),
                "expected UPDATE_PASSWORD required action, got " + rep.getRequiredActions());

        List<String> roles = userResource.roles().realmLevel().listAll().stream()
                .map(r -> r.getName()).toList();
        assertTrue(roles.contains("player"), "expected player role, got " + roles);
    }

    @Test
    void createUser_duplicateUsername_throwsConflict() {
        String username = uniqueUsername();
        UserCreateResponse first = service.createUser(username, "player");
        createdIds.add(first.id());

        assertThrows(util.UserConflictException.class,
                () -> service.createUser(username, "player"));
    }

    @Test
    void listUsers_includesFreshlyCreatedUser_andRespectsMax() {
        String username = uniqueUsername();
        UserCreateResponse created = service.createUser(username, "admin");
        createdIds.add(created.id());

        util.PageResponse<UserSummary> page = service.listUsers(0, 100);
        UserSummary found = page.items().stream()
                .filter(u -> username.equals(u.username()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("user not in listing"));

        assertEquals(created.id(), found.id());
        assertTrue(found.enabled());
        assertTrue(found.createdTimestamp() > 0L);
        assertTrue(found.roles().contains("admin"));
        assertTrue(found.roles().stream().noneMatch(r -> r.startsWith("default-roles-")),
                "default-roles-* should be filtered out, got " + found.roles());
        assertTrue(page.total() >= 1, "total should reflect realm user count");

        util.PageResponse<UserSummary> small = service.listUsers(0, 1);
        assertEquals(1, small.items().size());
        assertEquals(0, small.first());
        assertEquals(1, small.max());
    }

    @Test
    void resetPassword_returnsFreshPassword_andRearmsTemporaryFlag() {
        String username = uniqueUsername();
        UserCreateResponse created = service.createUser(username, "player");
        createdIds.add(created.id());

        String fresh = service.resetPassword(created.id());
        assertNotEquals(created.generatedPassword(), fresh);
        assertTrue(fresh.length() >= 16);

        var rep = keycloak.realm(REALM).users().get(created.id()).toRepresentation();
        assertTrue(rep.getRequiredActions().contains("UPDATE_PASSWORD"));
    }

    @Test
    void resetPassword_unknownUser_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void deleteUser_removesFromKeycloak_andSecondDelete404s() {
        String username = uniqueUsername();
        UserCreateResponse created = service.createUser(username, "player");

        service.deleteUser(created.id());

        var ex = assertThrows(WebApplicationException.class,
                () -> keycloak.realm(REALM).users().get(created.id()).toRepresentation());
        assertEquals(404, ex.getResponse().getStatus());

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteUser(created.id()));
    }

    private static String uniqueUsername() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
