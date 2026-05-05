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

## Architecture

REST API backend for a 2-player "Guess Who" card game. No auth, single active game at a time.

### Packages

- **`game`** — game logic, no persistence. `GameEngine` = state machine; `GameResource` = REST endpoints; `GameStatusResponse` = response DTO.
- **`pack`** / **`card`** — persistence. `Pack` and `Card` are Panache Active Record entities (`PanacheEntity`).
- **`image`** — S3 wrapper. `ImageService` uploads bytes, returns S3 key or presigned URL. Bucket name from `game.bucket.name` in `application.properties`.
- **`util`** — `IllegalStateExceptionMapper` maps `IllegalStateException` → HTTP 400.

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

### DTOs & misc

`CardDTO` and `GameStatusResponse` are Java Records. Entities/services use Lombok (`@RequiredArgsConstructor`, `@Getter`, etc.).

### CI

Push to `main` → GitHub Actions builds, generates OpenAPI spec, pushes to `Fileboss/qui-est-ce_back_API` (GitHub Pages). Tests skipped in CI; requires `API_TOKEN_GITHUB` secret.

### Roadmap

- Single game instance — no multi-session support.
- No game state persistence across restarts.
- No authentication.
