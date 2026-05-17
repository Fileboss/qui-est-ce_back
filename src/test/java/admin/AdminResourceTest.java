package admin;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import util.UserConflictException;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
class AdminResourceTest {

    @InjectMock
    KeycloakAdminService keycloakAdminService;

    // ---------- POST /admin/users ----------

    @Test
    void createUser_unauthenticated_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = "player")
    void createUser_asPlayer_returns403() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_validPlayerBody_returns201WithGeneratedPassword() {
        Mockito.when(keycloakAdminService.createUser("newplayer", "player"))
               .thenReturn(new UserCreateResponse("uid-1", "newplayer", "Generated!Password1"));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "newplayer", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(201)
                .body("id", is("uid-1"))
                .body("username", is("newplayer"))
                .body("generatedPassword", is("Generated!Password1"));

        Mockito.verify(keycloakAdminService).createUser("newplayer", "player");
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_validAdminBody_returns201() {
        Mockito.when(keycloakAdminService.createUser("newadmin", "admin"))
               .thenReturn(new UserCreateResponse("uid-2", "newadmin", "Generated!Password2"));

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "newadmin", "role", "admin"))
            .when().post("/admin/users")
            .then().statusCode(201);

        Mockito.verify(keycloakAdminService).createUser("newadmin", "admin");
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_duplicateUsername_returns409() {
        Mockito.doThrow(new UserConflictException("Username already exists: dup"))
               .when(keycloakAdminService).createUser(eq("dup"), any());

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "dup", "role", "player"))
            .when().post("/admin/users")
            .then()
                .statusCode(409)
                .body("Status", is("Failed"))
                .body("Error", containsString("dup"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_usernameTooShort_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "ab", "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_usernameTooLong_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "a".repeat(31), "role", "player"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void createUser_invalidRole_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice", "role", "superuser"))
            .when().post("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).createUser(any(), any());
    }

    // ---------- GET /admin/users ----------

    @Test
    void listUsers_unauthenticated_returns401() {
        given().when().get("/admin/users").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = "player")
    void listUsers_asPlayer_returns403() {
        given().when().get("/admin/users").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void listUsers_asAdmin_returnsList() {
        Mockito.when(keycloakAdminService.listUsers(0, 20))
               .thenReturn(List.of(
                   new UserSummary("id-a", "alice", true, 1700000000000L, List.of("player")),
                   new UserSummary("id-b", "bob", false, 1700000001000L, List.of("admin", "player"))
               ));

        given()
            .when().get("/admin/users")
            .then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].id", is("id-a"))
                .body("[0].username", is("alice"))
                .body("[0].enabled", is(true))
                .body("[0].createdTimestamp", is(1700000000000L))
                .body("[0].roles", equalTo(List.of("player")))
                .body("[1].roles", equalTo(List.of("admin", "player")));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void listUsers_passesPaginationParams() {
        Mockito.when(keycloakAdminService.listUsers(40, 10)).thenReturn(List.of());

        given()
            .queryParam("first", 40)
            .queryParam("max", 10)
            .when().get("/admin/users")
            .then().statusCode(200);

        Mockito.verify(keycloakAdminService).listUsers(40, 10);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void listUsers_maxOver100_returns400() {
        given()
            .queryParam("max", 101)
            .when().get("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).listUsers(anyInt(), anyInt());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void listUsers_negativeFirst_returns400() {
        given()
            .queryParam("first", -1)
            .when().get("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).listUsers(anyInt(), anyInt());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void listUsers_negativeMax_returns400() {
        given()
            .queryParam("max", -5)
            .when().get("/admin/users")
            .then().statusCode(400);

        Mockito.verify(keycloakAdminService, Mockito.never()).listUsers(anyInt(), anyInt());
    }

    // ---------- POST /admin/users/{id}/reset-password ----------

    @Test
    void resetPassword_unauthenticated_returns401() {
        given().when().post("/admin/users/some-id/reset-password").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = "player")
    void resetPassword_asPlayer_returns403() {
        given().when().post("/admin/users/some-id/reset-password").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void resetPassword_asAdmin_returns200WithGeneratedPassword() {
        Mockito.when(keycloakAdminService.resetPassword("id-a")).thenReturn("FreshPw1!Aaaaaaaa");

        given()
            .when().post("/admin/users/id-a/reset-password")
            .then().statusCode(200)
                .body("generatedPassword", is("FreshPw1!Aaaaaaaa"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void resetPassword_userNotFound_returns404() {
        Mockito.when(keycloakAdminService.resetPassword("missing"))
               .thenThrow(new IllegalArgumentException("User not found: missing"));

        given()
            .when().post("/admin/users/missing/reset-password")
            .then().statusCode(404);
    }

    // ---------- DELETE /admin/users/{id} ----------

    @Test
    void deleteUser_unauthenticated_returns401() {
        given().when().delete("/admin/users/some-id").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = "player")
    void deleteUser_asPlayer_returns403() {
        given().when().delete("/admin/users/some-id").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    @OidcSecurity(claims = @Claim(key = "sub", value = "other-admin-id"))
    void deleteUser_asAdmin_returns204() {
        given()
            .when().delete("/admin/users/target-id")
            .then().statusCode(204);

        Mockito.verify(keycloakAdminService).deleteUser("target-id");
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    @OidcSecurity(claims = @Claim(key = "sub", value = "self-id"))
    void deleteUser_self_returns400() {
        given()
            .accept(ContentType.JSON)
            .when().delete("/admin/users/self-id")
            .then().statusCode(400)
                .body("Error", containsString("own account"));

        Mockito.verify(keycloakAdminService, Mockito.never()).deleteUser(any());
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    @OidcSecurity(claims = @Claim(key = "sub", value = "other-admin-id"))
    void deleteUser_userNotFound_returns404() {
        Mockito.doThrow(new IllegalArgumentException("User not found: missing"))
               .when(keycloakAdminService).deleteUser("missing");

        given()
            .when().delete("/admin/users/missing")
            .then().statusCode(404);
    }
}
