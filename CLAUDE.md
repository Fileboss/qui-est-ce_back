# CLAUDE.md

## Commands

```bash
./mvnw compile quarkus:dev          # Dev mode w/ hot reload (needs Docker)
./mvnw test -f pom.xml              # All tests
./mvnw test -Dtest=GameResourceTest # Single test class (default: run all)
./mvnw clean package -DskipTests   # Full build → OpenAPI spec at target/generated/swagger/
./mvnw quarkus:build -f pom.xml    # Fast build (~2.5s, same OpenAPI output)
./mvnw quarkus:add-extension -Dextensions="<name>"
```

Dev Services auto-provision PostgreSQL and MinIO (`game-images` bucket) via Testcontainers in dev/test — no manual setup needed.

## Code quality

All code must be compliant with **SonarQube for IDE** default rules (no blocker, critical, or major issues). Run the analysis in your IDE before committing.

Key references that inform design decisions in this codebase:
- *Designing Data-Intensive Applications* — Martin Kleppmann (data modeling, consistency, state management)
- *Effective Java* — Joshua Bloch (API design, immutability, concurrency, Java idioms)

## Architecture

REST API backend for a 2-player "Guess Who" card game. No auth, single active game at a time.

### Packages

- **`game`** — game logic, no persistence. `GameEngine` = state machine; `GameResource` = REST endpoints; `GameStatusResponse` = response DTO.
- **`pack`** / **`card`** — persistence. `Pack` and `Card` are Panache Active Record entities (`PanacheEntity`).
- **`game` (WebSocket)** — `GameWebSocket` (`/ws/game/{gameId}`), `GamesWebSocket` (`/ws/games`), `GameUpdateBroadcaster` (CDI observer → broadcast), `GameUpdateEvent` (push payload).
- **`image`** — S3 wrapper. `ImageService` uploads bytes, returns S3 key or presigned URL. Bucket name from `game.bucket.name` in `application.properties`.
- **`util`** — exception infrastructure: `IllegalStateExceptionMapper` (→ 400), `IllegalArgumentExceptionMapper` (→ 404), `NumberFormatExceptionMapper` (→ 400), `JsonSerializationException` (unchecked wrapper for `JsonProcessingException` in WebSocket/CDI-async contexts where checked throws cannot be declared).

### State machine

`GameEngine` is `@ApplicationScoped`. Illegal transitions throw `IllegalStateException` → 400.

```
NOT_STARTED → PREPARING      (create)
PREPARING   → STARTED        (start; both player1/join + player2/join must precede)
STARTED     → PLAYER_X_WINS  (playerX/guess with correct cardId)
PLAYER_X_WINS → NOT_STARTED  (reset)
```

**Key quirk:** `player1/join` returns the card Player 1 must guess (Player 2's target), and vice versa — cross-assignment is intentional.

State-mutating methods are `synchronized`; `getPlayerXCardDTOToGuess` getters are not (read-only, `PREPARING` only).

### WebSocket

Two endpoints push state changes to connected clients:

| Endpoint | Purpose |
|----------|---------|
| `ws://…/ws/game/{gameId}` | Per-game: fires on every state transition and guess result |
| `ws://…/ws/games` | Lobby: fires when any game is created or deleted |

- **`GameWebSocket`** / **`GamesWebSocket`** — `@WebSocket` endpoint classes. Each has an `@OnOpen` method that pushes the current state to the connecting client: `GameWebSocket` sends a `STATE_CHANGE` event with the current game state; `GamesWebSocket` sends the full `List<GameDTO>` as JSON. Subsequent pushes are handled by `GameUpdateBroadcaster` via `OpenConnections`.
- **`GameUpdateBroadcaster`** — `@ApplicationScoped` CDI observer (`@ObservesAsync GameUpdateEvent`). Filters `OpenConnections` by path param and broadcasts JSON. **Critical:** events must be fired with `fireAsync()` (not `fire()`); `fire()` triggers `@Observes` (synchronous) and will never reach `@ObservesAsync`.
- **`GameUpdateEvent`** — record payload: `gameId`, `type`, `gameState` (null on `DELETED`), `correct` (non-null on guess only). Annotated `@JsonInclude(NON_NULL)` so null fields are omitted from JSON.

Event types fired from `GameRegistry` after each mutation:

| Trigger | `type` | Sent to |
|---------|--------|---------|
| `createGame` | `GAME_CREATED` | `/ws/games` |
| `startGame`, guess, `resetGame` | `STATE_CHANGE` | `/ws/game/{gameId}` |
| `removeGame` | `DELETED` | both |

### DTOs & misc

`CardDTO` and `GameStatusResponse` are Java Records. Entities/services use Lombok (`@RequiredArgsConstructor`, `@Getter`, etc.).

### CI

Push to `main` → GitHub Actions builds, generates OpenAPI spec, pushes to `Fileboss/qui-est-ce_back_API` (GitHub Pages). Tests skipped in CI; requires `API_TOKEN_GITHUB` secret.

### Roadmap

- No game state persistence across restarts.
- No authentication.
