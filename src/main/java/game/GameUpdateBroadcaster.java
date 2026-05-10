package game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import util.JsonSerializationException;

@RequiredArgsConstructor
@ApplicationScoped
public class GameUpdateBroadcaster {

    private final OpenConnections openConnections;
    private final ObjectMapper objectMapper;

    public void onGameUpdate(@Observes GameUpdateEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException(e);
        }
        switch (event.type()) {
            case "GAME_CREATED" -> broadcastToLobby(json);
            case "DELETED" -> {
                broadcastToGame(event.gameId(), json);
                broadcastToLobby(json);
            }
            default -> broadcastToGame(event.gameId(), json);
        }
    }

    private void broadcastToGame(String gameId, String json) {
        openConnections.listAll().stream()
                .filter(c -> gameId.equals(c.pathParam("gameId")))
                .forEach(c -> sendSafely(c, json));
    }

    private void broadcastToLobby(String json) {
        openConnections.listAll().stream()
                .filter(c -> c.pathParam("gameId") == null)
                .forEach(c -> sendSafely(c, json));
    }

    private static void sendSafely(WebSocketConnection c, String json) {
        try {
            c.sendTextAndAwait(json);
        } catch (RuntimeException e) {
            Log.warnf(e, "WS broadcast failed for connection %s; continuing", c.id());
        }
    }
}
