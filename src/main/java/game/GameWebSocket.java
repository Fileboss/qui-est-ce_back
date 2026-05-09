package game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import lombok.RequiredArgsConstructor;
import util.JsonSerializationException;

@WebSocket(path = "/ws/game/{gameId}")
@Authenticated
@RequiredArgsConstructor
public class GameWebSocket {

    private final GameRegistry gameRegistry;
    private final ObjectMapper objectMapper;

    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        String gameId = connection.pathParam("gameId");
        GameUpdateEvent event = gameRegistry.findGame(gameId)
                .map(engine -> new GameUpdateEvent(gameId, "STATE_CHANGE", engine.getGameState().toString(), null, null))
                .orElseGet(() -> new GameUpdateEvent(gameId, "DELETED", null, null, null));
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException(e);
        }
    }
}
