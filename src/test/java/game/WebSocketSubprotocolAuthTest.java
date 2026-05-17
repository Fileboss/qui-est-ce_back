package game;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.UpgradeRejectedException;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebSocketSubprotocolAuthTest {

    private static final String KEYCLOAK_TOKEN_URL =
            "http://localhost:8180/realms/qui-est-ce/protocol/openid-connect/token";
    private static final String QUARKUS_HEADER_PREFIX = "quarkus-http-upgrade#Authorization#Bearer ";
    private static final String CARRIER_SUBPROTOCOL = "bearer-token-carrier";

    @TestHTTPResource("/ws/games")
    URI gamesUri;

    private Vertx vertx;
    private HttpClient client;
    private String accessToken;

    @BeforeAll
    void setUp() {
        vertx = Vertx.vertx();
        client = vertx.createHttpClient();
        accessToken = given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", "qui-est-ce-back")
                .formParam("client_secret", "dev-secret")
                .when().post(KEYCLOAK_TOKEN_URL)
                .then().statusCode(200)
                .extract().path("access_token");
    }

    @AfterAll
    void tearDown() {
        client.close();
        vertx.close();
    }

    @Test
    void connect_withValidSubprotocolToken_succeeds() throws Exception {
        String encoded = URLEncoder.encode(QUARKUS_HEADER_PREFIX + accessToken, StandardCharsets.UTF_8)
                .replace("+", "%20");
        WebSocketConnectOptions opts = baseOptions()
                .addSubProtocol(CARRIER_SUBPROTOCOL)
                .addSubProtocol(encoded);

        WebSocket ws = connect(opts).get(5, TimeUnit.SECONDS);
        try {
            assertThat(ws.subProtocol()).isEqualTo(CARRIER_SUBPROTOCOL);
        } finally {
            ws.close();
        }
    }

    @Test
    void connect_withoutToken_isRejectedWith401() {
        assertThatThrownBy(() -> connect(baseOptions()).get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(UpgradeRejectedException.class)
                .satisfies(t -> assertThat(((UpgradeRejectedException) t.getCause()).getStatus()).isEqualTo(401));
    }

    private WebSocketConnectOptions baseOptions() {
        return new WebSocketConnectOptions()
                .setHost(gamesUri.getHost())
                .setPort(gamesUri.getPort())
                .setURI(gamesUri.getPath());
    }

    private CompletableFuture<WebSocket> connect(WebSocketConnectOptions opts) {
        CompletableFuture<WebSocket> future = new CompletableFuture<>();
        client.webSocket(opts, ar -> {
            if (ar.succeeded()) future.complete(ar.result());
            else future.completeExceptionally(ar.cause());
        });
        return future;
    }
}
