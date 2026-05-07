# CLAUDE.md

## Commands

```bash
./mvnw compile quarkus:dev          # Dev mode w/ hot reload (needs Docker)
./mvnw test                         # All tests
./mvnw test -Dtest=GameResourceTest # Single test class
./mvnw clean package -DskipTests    # Full build → OpenAPI at target/generated/swagger/
./mvnw quarkus:add-extension -Dextensions="<name>"
```

Dev Services auto-provision PostgreSQL, MinIO (`game-images` bucket) and Keycloak via Testcontainers.

All code must be SonarQube-clean (no blocker/critical/major). Design references: *Designing Data-Intensive Applications* (Kleppmann), *Effective Java* (Bloch).

## Architecture

REST + WebSocket backend for a 2-player "Guess Who" card game. Single active game at a time.

### Packages

- **`game`** — game logic (no persistence). `GameEngine` = state machine; `GameResource` = REST; `GameRegistry` = per-game `GameEngine` map + fires update events.
- **`game` (WebSocket)** — `GameWebSocket` (`/ws/game/{gameId}`), `GamesWebSocket` (`/ws/games`), `GameUpdateBroadcaster` (CDI observer → broadcast), `GameUpdateEvent` (push payload).
- **`pack`** / **`card`** — Panache Active Record entities.
- **`image`** — S3 wrapper (`ImageService`); bucket name from `game.bucket.name`.
- **`util`** — exception mappers (`IllegalStateException`→400, `IllegalArgumentException`→404, `NumberFormatException`→400), `JsonSerializationException` (unchecked wrapper for `JsonProcessingException` in WebSocket/CDI-async contexts), `WebSocketTokenFilter` (see Security).

### State machine

`GameEngine` is `@ApplicationScoped`. Illegal transitions throw `IllegalStateException` → 400. State-mutating methods are `synchronized`; `getPlayerXCardDTOToGuess` getters aren't (read-only, `PREPARING` only).

```
NOT_STARTED → PREPARING      (create)
PREPARING   → STARTED        (start; both player1/join + player2/join must precede)
STARTED     → PLAYER_X_WINS  (playerX/guess with correct cardId)
PLAYER_X_WINS → NOT_STARTED  (reset)
```

**Key quirk:** `player1/join` returns the card Player 1 must guess (Player 2's target), and vice versa — cross-assignment is intentional.

### WebSocket

| Endpoint | Purpose |
|----------|---------|
| `ws://…/ws/game/{gameId}` | Per-game: every state transition + guess result |
| `ws://…/ws/games` | Lobby: any game created or deleted |

- **`GameWebSocket`** / **`GamesWebSocket`** — `@WebSocket` classes. Each `@OnOpen` pushes current state to the connecting client (`GameWebSocket` → `STATE_CHANGE` event; `GamesWebSocket` → full `List<GameDTO>`). Subsequent pushes go through `GameUpdateBroadcaster` via `OpenConnections`.
- **`GameUpdateBroadcaster`** — `@ApplicationScoped` CDI observer (`@ObservesAsync GameUpdateEvent`), filters `OpenConnections` by path param, broadcasts JSON. **Must use `fireAsync()`** — `fire()` only triggers `@Observes` (sync) and never reaches `@ObservesAsync`.
- **`GameUpdateEvent`** — record `(gameId, type, gameState, correct)`. `@JsonInclude(NON_NULL)` so null fields are omitted.

Events fired from `GameRegistry` after each mutation:

| Trigger | `type` | Sent to |
|---------|--------|---------|
| `createGame` | `GAME_CREATED` | `/ws/games` |
| `startGame`, guess, `resetGame` | `STATE_CHANGE` | `/ws/game/{gameId}` |
| `removeGame` | `DELETED` | both |

### DTOs & misc

`CardDTO`, `GameStatusResponse`, `GameUpdateEvent` are Java Records. Entities/services use Lombok (`@RequiredArgsConstructor`, `@Getter`, …).

### Security

OIDC + Keycloak (`quarkus-oidc`). Dev Services auto-provision Keycloak on `quarkus:dev` and import `realm-export.json` (realm: `qui-est-ce`).

| Role | Permissions |
|------|------------|
| `player` | Read everything; create/join/start/guess/reset/delete games |
| `admin` | Composite of `player` + manage packs/cards (CRUD) |

Test users (password `password`): `player1`, `player2` (player), `admin` (admin). Get a token via the Keycloak port shown in Dev UI → OpenID Connect card (see README for the curl command).

**WebSocket auth — important.** Browsers cannot set headers on the `WebSocket` API, so the front sends `?access_token=<jwt>`. Quarkus has **no native config** to read bearer tokens from query strings. `util/WebSocketTokenFilter` is a `@RouteFilter(500)` (provided by `quarkus-reactive-routes`) that copies `?access_token=…` into `Authorization: Bearer …` for `/ws/*` paths *before* the OIDC handler runs. After that, `@Authenticated` works normally. The README has the full explanation; do not "simplify" by removing the filter — both WS endpoints will start returning 401.

### CI

Push to `main` → GitHub Actions builds, generates OpenAPI, pushes to `Fileboss/qui-est-ce_back_API` (GitHub Pages). Tests skipped in CI; requires `API_TOKEN_GITHUB` secret.

### Roadmap

- No game state persistence across restarts.
