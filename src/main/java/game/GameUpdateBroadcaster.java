package game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import lombok.RequiredArgsConstructor;
import util.JsonSerializationException;

@RequiredArgsConstructor
@ApplicationScoped
public class GameUpdateBroadcaster {

    private final OpenConnections openConnections;
    private final ObjectMapper objectMapper;

    public void onGameUpdate(@ObservesAsync GameUpdateEvent event) {
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
                .forEach(c -> c.sendTextAndAwait(json));
    }

    private void broadcastToLobby(String json) {
        openConnections.listAll().stream()
                .filter(c -> c.pathParam("gameId") == null)
                .forEach(c -> c.sendTextAndAwait(json));
    }
}
