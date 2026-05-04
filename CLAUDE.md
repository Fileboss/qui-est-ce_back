# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode with hot reload (requires Docker for Dev Services)
./mvnw compile quarkus:dev

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=GameResourceTest

# Build (skips tests, generates OpenAPI spec under target/generated/swagger/)
./mvnw clean package -DskipTests

# Add a Quarkus extension
./mvnw quarkus:add-extension -Dextensions="<extension-name>"
```

Dev Services auto-provision a PostgreSQL database and a MinIO S3 bucket (`game-images`) via Testcontainers when running in dev or test mode — no manual infrastructure setup needed. Docker must be running.

## Architecture

The app is a REST API backend for a 2-player "Guess Who" card game. There is no auth and a single active game at a time.

### Packages

- **`game`** — game logic only; no persistence. `GameEngine` owns the state machine; `GameResource` exposes the REST endpoints; `GameStatusResponse` is the response DTO.
- **`pack`** and **`card`** — persistence layer. `Pack` and `Card` are Panache Active Record entities (they extend `PanacheEntity`). Services and resources follow the standard Quarkus pattern.
- **`image`** — S3 wrapper. `ImageService` uploads raw bytes and returns either the S3 key or a presigned URL. The bucket name is read from `game.bucket.name` in `application.properties`.
- **`util`** — `IllegalStateExceptionMapper` maps `IllegalStateException` to HTTP 400; all game state violations throw this.

### Game state machine

`GameEngine` is `@ApplicationScoped` (singleton). Its `GameState` enum drives all validation. Illegal transitions throw `IllegalStateException` which the mapper converts to 400.

```
NOT_STARTED → PREPARING (create)
PREPARING   → STARTED   (start)
             ← player1/join + player2/join must happen before start
STARTED     → PLAYER_1_WINS / PLAYER_2_WINS (playerX/guess with correct cardId)
PLAYER_X_WINS → NOT_STARTED (reset)
```

Key quirk: `player1/join` returns the card Player 1 must guess (i.e. Player 2's target), and vice versa. The cross-assignment is intentional.

All methods that mutate state are `synchronized`; the two `getPlayerXCardDTOToGuess` getters are not (they are read-only and only callable in `PREPARING`).

### DTOs

`CardDTO` is a Java Record (`id`, `name`, `imageUrl`, `packId`). `GameStatusResponse` is also a Record. Lombok is used on entities and services (`@RequiredArgsConstructor`, `@Getter`, etc.).

### CI

On push to `main`, GitHub Actions builds the project, generates the OpenAPI spec, and pushes it to the `Fileboss/qui-est-ce_back_API` repository (GitHub Pages). Tests are skipped in CI; the `API_TOKEN_GITHUB` secret is required.

### Roadmap (known limitations)
- Single game instance — no multi-session support.
- No persistence of game state across restarts.
- No authentication on any endpoint.
