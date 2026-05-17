package util;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(CorsPreflightTest.CorsProdLikeProfile.class)
@TestSecurity(user = "admin", roles = "admin")
class CorsPreflightTest {

    private static final String ALLOWED = "https://qui-est-qui.lepgu.fr";

    @Test
    void preflight_fromAllowedOrigin_echoesOrigin() {
        given()
            .header("Origin", ALLOWED)
            .header("Access-Control-Request-Method", "POST")
            .when().options("/pack")
            .then().statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo(ALLOWED))
                .header("Access-Control-Allow-Methods", containsString("POST"));
    }

    @Test
    void preflight_fromForeignOrigin_omitsAllowOrigin() {
        given()
            .header("Origin", "https://evil.example.com")
            .header("Access-Control-Request-Method", "POST")
            .when().options("/pack")
            .then()
                .header("Access-Control-Allow-Origin", emptyOrNullString());
    }

    @Test
    void getWithoutOrigin_stillWorks() {
        given()
            .when().get("/pack")
            .then().statusCode(200);
    }

    public static class CorsProdLikeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                // CORS itself is armed in application.properties; just fill in the
                // allowed origin for this profile.
                "quarkus.http.cors.origins", ALLOWED,
                // Spawning a second Keycloak Dev Services would collide on the pinned
                // 8180 port — @TestSecurity synthesizes identity, so OIDC is unused.
                "quarkus.keycloak.devservices.enabled", "false",
                "quarkus.oidc.tenant-enabled", "false"
            );
        }
    }
}
