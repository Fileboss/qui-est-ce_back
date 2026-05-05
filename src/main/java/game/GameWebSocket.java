package game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import lombok.RequiredArgsConstructor;
import util.JsonSerializationException;

@WebSocket(path = "/ws/game/{gameId}")
@RequiredArgsConstructor
public class GameWebSocket {

    private final GameRegistry gameRegistry;
    private final ObjectMapper objectMapper;

    @OnOpen
    public String onOpen(WebSocketConnection connection) {
        String gameId = connection.pathParam("gameId");
        GameEngine engine = gameRegistry.getGame(gameId);
        try {
            return objectMapper.writeValueAsString(
                    new GameUpdateEvent(gameId, "STATE_CHANGE", engine.getGameState().toString(), null));
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException(e);
        }
    }
}
