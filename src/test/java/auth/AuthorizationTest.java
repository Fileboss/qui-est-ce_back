package auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Guards the auth wiring: anonymous requests must be rejected with 401, and
 * an authenticated user lacking the required role must be rejected with 403.
 * Class deliberately has no class-level {@code @TestSecurity}; each test
 * controls its identity explicitly.
 */
@QuarkusTest
class AuthorizationTest {

    @Test
    void getPack_unauthenticated_returns401() {
        given()
            .when().get("/pack")
            .then().statusCode(401);
    }

    @Test
    void getGame_unauthenticated_returns401() {
        given()
            .when().get("/game")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob", roles = "player")
    void createPack_asPlayer_returns403() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Forbidden"))
            .when().post("/pack/create")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bob", roles = "player")
    void deleteCard_asPlayer_returns403() {
        given()
            .when().delete("/card/1")
            .then().statusCode(403);
    }
}
