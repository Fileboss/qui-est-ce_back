package admin;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import util.UserConflictException;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
class AdminResourceTest {

    @InjectMock
    KeycloakAdminService keycloakAdminService;

    @Test
    void createUser_unauthenticated_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "password", "Securepassword1", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = "player")
    void createUser_asPlayer_returns403() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "password", "Securepassword1", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_validPlayerBody_returns201() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "newplayer", "password", "Securepassword1", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(201);

        Mockito.verify(keycloakAdminService).createUser("newplayer", "Securepassword1", "player");
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_validAdminBody_returns201() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "newadmin", "password", "Securepassword1", "role", "admin"))
            .when().post("/admin/users")
            .then().statusCode(201);

        Mockito.verify(keycloakAdminService).createUser("newadmin", "Securepassword1", "admin");
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_duplicateUsername_returns409() {
        Mockito.doThrow(new UserConflictException("Username already exists: dup"))
               .when(keycloakAdminService).createUser(eq("dup"), any(), any());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "dup", "password", "Securepassword1", "role", "player"))
            .when().post("/admin/users")
            .then()
                .statusCode(409)
                .body("Status", is("Failed"))
                .body("Error", containsString("dup"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_passwordTooShort_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "password", "short", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_passwordMissingComplexity_returns400() {
        // 12+ chars but no uppercase letter — fails the complexity rule.
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "password", "alllowercase1", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_usernameTooShort_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "ab", "password", "Securepassword1", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_usernameTooLong_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "a".repeat(31), "password", "Securepassword1", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_invalidRole_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "password", "Securepassword1", "role", "superuser"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any(), any());
    }
}
