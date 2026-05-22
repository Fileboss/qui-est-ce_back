package game;

import card.CardDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameWebSocketChatTest {

    private static final String KEYCLOAK_TOKEN_URL =
            "http://localhost:8180/realms/qui-est-ce/protocol/openid-connect/token";
    private static final String QUARKUS_HEADER_PREFIX = "quarkus-http-upgrade#Authorization#Bearer ";
    private static final String CARRIER_SUBPROTOCOL = "bearer-token-carrier";

    @TestHTTPResource("/ws/game/")
    URI gameWsBase;

    @Inject
    GameRegistry gameRegistry;

    @Inject
    ObjectMapper objectMapper;

    private Vertx vertx;
    private HttpClient client;
    private String player1Token;
    private String player2Token;
    private String adminToken;

    @BeforeAll
    void setUp() {
        vertx = Vertx.vertx();
        client = vertx.createHttpClient();
        player1Token = passwordToken("player1");
        player2Token = passwordToken("player2");
        adminToken = passwordToken("admin");
    }

    @AfterAll
    void tearDown() {
        client.close();
        vertx.close();
    }

    private String passwordToken(String username) {
        return given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("client_id", "qui-est-ce-back")
                .formParam("client_secret", "dev-secret")
                .formParam("username", username)
                .formParam("password", "password")
                .when().post(KEYCLOAK_TOKEN_URL)
                .then().statusCode(200)
                .extract().path("access_token");
    }

    private String newGameWithPlayers() {
        GameDTO game = gameRegistry.createGame(List.of(new CardDTO("1", "Alice", "http://img/1", "1")));
        gameRegistry.join(game.gameId(), "player1");
        gameRegistry.join(game.gameId(), "player2");
        return game.gameId();
    }

    private CapturingConnection connect(String gameId, String token) throws Exception {
        String encoded = URLEncoder.encode(QUARKUS_HEADER_PREFIX + token, StandardCharsets.UTF_8)
                .replace("+", "%20");
        WebSocketConnectOptions opts = new WebSocketConnectOptions()
                .setHost(gameWsBase.getHost())
                .setPort(gameWsBase.getPort())
                .setURI(gameWsBase.getPath() + gameId)
                .addSubProtocol(CARRIER_SUBPROTOCOL)
                .addSubProtocol(encoded);
        CompletableFuture<WebSocket> wsFuture = new CompletableFuture<>();
        client.webSocket(opts, ar -> {
            if (ar.succeeded()) wsFuture.complete(ar.result());
            else wsFuture.completeExceptionally(ar.cause());
        });
        WebSocket ws = wsFuture.get(5, TimeUnit.SECONDS);
        CapturingConnection cc = new CapturingConnection(ws);
        cc.discardNext();  // initial @OnOpen STATE_CHANGE frame
        return cc;
    }

    @Test
    void chatMessage_isBroadcastToBothParticipants() throws Exception {
        String gameId = newGameWithPlayers();
        CapturingConnection p1 = connect(gameId, player1Token);
        CapturingConnection p2 = connect(gameId, player2Token);
        try {
            p1.send("{\"type\":\"CHAT_MESSAGE\",\"text\":\"hello\"}");

            JsonNode received1 = p1.awaitNext();
            JsonNode received2 = p2.awaitNext();

            for (JsonNode received : List.of(received1, received2)) {
                assertThat(received.get("type").asText()).isEqualTo("CHAT_MESSAGE");
                assertThat(received.get("gameId").asText()).isEqualTo(gameId);
                assertThat(received.get("senderSub").asText()).isEqualTo("player1");
                assertThat(received.get("senderName").asText()).isEqualTo("player1");
                assertThat(received.get("text").asText()).isEqualTo("hello");
                assertThat(received.has("gameState")).isFalse();
            }
        } finally {
            p1.close();
            p2.close();
        }
    }

    @Test
    void chatMessage_fromNonParticipant_returnsErrorOnlyToSender() throws Exception {
        String gameId = newGameWithPlayers();
        CapturingConnection p1 = connect(gameId, player1Token);
        CapturingConnection intruder = connect(gameId, adminToken);
        try {
            intruder.send("{\"type\":\"CHAT_MESSAGE\",\"text\":\"hi\"}");

            JsonNode err = intruder.awaitNext();
            assertThat(err.get("type").asText()).isEqualTo("CHAT_ERROR");
            assertThat(err.get("text").asText()).isEqualTo("not a participant");

            assertThat(p1.pollNext(500)).isNull();
        } finally {
            p1.close();
            intruder.close();
        }
    }

    @Test
    void chatMessage_oversizedText_isRejectedWithoutBroadcast() throws Exception {
        String gameId = newGameWithPlayers();
        CapturingConnection p1 = connect(gameId, player1Token);
        CapturingConnection p2 = connect(gameId, player2Token);
        try {
            String oversized = "x".repeat(GameWebSocket.MAX_CHAT_TEXT + 1);
            p1.send(objectMapper.writeValueAsString(new TestInbound("CHAT_MESSAGE", oversized)));

            JsonNode err = p1.awaitNext();
            assertThat(err.get("type").asText()).isEqualTo("CHAT_ERROR");
            assertThat(err.get("text").asText()).contains("exceeds");

            assertThat(p2.pollNext(500)).isNull();
        } finally {
            p1.close();
            p2.close();
        }
    }

    @Test
    void chatMessage_blankText_isRejected() throws Exception {
        String gameId = newGameWithPlayers();
        CapturingConnection p1 = connect(gameId, player1Token);
        CapturingConnection p2 = connect(gameId, player2Token);
        try {
            p1.send("{\"type\":\"CHAT_MESSAGE\",\"text\":\"   \"}");

            JsonNode err = p1.awaitNext();
            assertThat(err.get("type").asText()).isEqualTo("CHAT_ERROR");
            assertThat(err.get("text").asText()).contains("blank");

            assertThat(p2.pollNext(500)).isNull();
        } finally {
            p1.close();
            p2.close();
        }
    }

    @Test
    void chatMessage_malformedJson_replies_then_subsequentValidChatStillWorks() throws Exception {
        String gameId = newGameWithPlayers();
        CapturingConnection p1 = connect(gameId, player1Token);
        CapturingConnection p2 = connect(gameId, player2Token);
        try {
            p1.send("not-json");
            JsonNode err = p1.awaitNext();
            assertThat(err.get("type").asText()).isEqualTo("CHAT_ERROR");
            assertThat(err.get("text").asText()).isEqualTo("malformed message");
            assertThat(p2.pollNext(500)).isNull();

            p1.send("{\"type\":\"CHAT_MESSAGE\",\"text\":\"after recovery\"}");
            JsonNode ok1 = p1.awaitNext();
            JsonNode ok2 = p2.awaitNext();
            assertThat(ok1.get("type").asText()).isEqualTo("CHAT_MESSAGE");
            assertThat(ok1.get("text").asText()).isEqualTo("after recovery");
            assertThat(ok2.get("text").asText()).isEqualTo("after recovery");
        } finally {
            p1.close();
            p2.close();
        }
    }

    @Test
    void unknownInboundType_returnsError() throws Exception {
        String gameId = newGameWithPlayers();
        CapturingConnection p1 = connect(gameId, player1Token);
        CapturingConnection p2 = connect(gameId, player2Token);
        try {
            p1.send("{\"type\":\"WAT\",\"text\":\"x\"}");

            JsonNode err = p1.awaitNext();
            assertThat(err.get("type").asText()).isEqualTo("CHAT_ERROR");
            assertThat(err.get("text").asText()).isEqualTo("unsupported message type");

            assertThat(p2.pollNext(500)).isNull();
        } finally {
            p1.close();
            p2.close();
        }
    }

    private final class CapturingConnection {
        private final WebSocket ws;
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

        CapturingConnection(WebSocket ws) {
            this.ws = ws;
            ws.textMessageHandler(frames::add);
        }

        void send(String text) {
            ws.writeTextMessage(text);
        }

        void discardNext() throws Exception {
            String raw = frames.poll(5, TimeUnit.SECONDS);
            assertThat(raw).as("expected initial frame").isNotNull();
        }

        JsonNode awaitNext() throws Exception {
            String raw = frames.poll(5, TimeUnit.SECONDS);
            assertThat(raw).as("expected a frame within 5s").isNotNull();
            return objectMapper.readTree(raw);
        }

        JsonNode pollNext(long millis) throws Exception {
            String raw = frames.poll(millis, TimeUnit.MILLISECONDS);
            return raw == null ? null : objectMapper.readTree(raw);
        }

        void close() {
            ws.close();
        }
    }

    private record TestInbound(String type, String text) {}
}
