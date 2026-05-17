# CLAUDE.md

## Commands

```bash
./mvnw compile quarkus:dev          # Dev mode (needs Docker)
./mvnw test                         # All tests
./mvnw test -Dtest=GameEngineTest   # Single test class
./mvnw clean package -DskipTests    # Build → OpenAPI at target/generated/swagger/
./mvnw quarkus:add-extension -Dextensions="<name>"
```

Dev Services auto-provision PostgreSQL, MinIO (`game-images` bucket) and Keycloak via Testcontainers. Dev DB container is **reused between restarts** (`%dev.quarkus.datasource.devservices.reuse=true`) — data survives `Ctrl-C` / relaunch. Schema is owned by **Flyway** (`db/migration/`); never let Hibernate generate DDL. Prod profile reads all infra coordinates from env vars — see `.env.example` at the root (run with `QUARKUS_PROFILE=prod`). Code must be SonarQube-clean (no blocker/critical/major). Design refs: *DDIA* (Kleppmann), *Effective Java* (Bloch). Roadmap: `ROADMAP.md`.

## Architecture

REST + WebSocket backend for a 2-player "Guess Who" game. In-memory; one `GameEngine` per game id.

### Packages

- **`game`** — `GameEngine` (state machine, no persistence), `GameRegistry` (`@ApplicationScoped` map of engines + event firing), `GameResource` (REST), `GameWebSocket` (`/ws/game/{gameId}`), `GamesWebSocket` (`/ws/games`), `GameUpdateBroadcaster` (CDI observer → broadcast).
- **`pack`** / **`card`** — Panache Active Record entities.
- **`image`** — `ImageService` (S3 wrapper); bucket from `game.bucket.name`.
- **`util`** — exception mappers: `IllegalStateException`→400, `IllegalArgumentException`→404, `NumberFormatException`→400, `PlayerConflictException`→409. Plus `JsonSerializationException` (unchecked wrapper for `JsonProcessingException` in WS/CDI-async contexts).

### State machine

`GameEngine` mutating methods are `synchronized`; state fields are `volatile`. Illegal transitions throw `IllegalStateException` → 400.

```
NOT_STARTED → PREPARING      (create)
PREPARING   → STARTED        (start; both player1Join + player2Join must precede)
STARTED     → PLAYER_X_WINS  (playerX/guess with correct cardId)
PLAYER_X_WINS → NOT_STARTED  (reset; clears subs)
```

**Player identity.** Each join records the caller's principal name in `player1Sub` / `player2Sub`. If the same sub tries to take the other slot, `player1Join`/`player2Join` throws `PlayerConflictException` → 409. Re-joining the same slot is idempotent. `start()` requires both subs non-null. `GameResource` injects `SecurityIdentity` and passes `identity.getPrincipal().getName()` through.

**Cross-assignment quirk** (lives inside `GameEngine`): `player1Join` returns `player2CardDTOToGuess` and vice versa — i.e. each join returns the card the *other* player must guess. Intentional.

### WebSocket

| Endpoint | Purpose |
|----------|---------|
| `ws://…/ws/game/{gameId}` | Per-game state transitions, joins, guess results |
| `ws://…/ws/games` | Lobby: game created/deleted |

- `@OnOpen` pushes current state to the connecting client. Subsequent pushes go through `GameUpdateBroadcaster` via `OpenConnections`.
- **`GameUpdateBroadcaster`** observes `@ObservesAsync GameUpdateEvent` and filters connections by path param. **Must use `Event.fireAsync()`** — `fire()` only triggers sync `@Observes` and never reaches `@ObservesAsync`.
- **`GameUpdateEvent`** — record `(gameId, type, gameState, correct, playersJoined)`. `@JsonInclude(NON_NULL)` strips null fields. `playersJoined` is set on `player1Join`/`player2Join` events only.

Events fired from `GameRegistry`:

| Trigger | `type` | Channel |
|---------|--------|---------|
| `createGame` | `GAME_CREATED` | `/ws/games` |
| `player1Join`, `player2Join`, `startGame`, guess, `resetGame` | `STATE_CHANGE` | `/ws/game/{gameId}` |
| `removeGame` | `DELETED` | both |

### DTOs & misc

`CardDTO`, `GameStatusResponse`, `GameUpdateEvent` are records. Entities/services use Lombok (`@RequiredArgsConstructor`, `@Getter`, …).

**Hibernate naming.** Quarkus 3 uses Hibernate's default passthrough `PhysicalNamingStrategyStandardImpl` — no camelCase→snake_case conversion. `imageUrl` maps to `imageurl` in PostgreSQL (lowercased by the driver, no underscore). Reflect this in any new Flyway migration.

### Security

OIDC + Keycloak (`quarkus-oidc`). Dev Services auto-provision Keycloak and import `realm-export.json` (realm: `qui-est-ce`).

| Role | Permissions |
|------|------------|
| `player` | Read all; create/join/start/guess/reset/delete games |
| `admin` | `player` + manage packs/cards (CRUD) |

Test users (password `password`): `player1`, `player2` (player), `admin` (admin). Token via Keycloak port shown in Dev UI → OpenID Connect card (see README).

**WebSocket auth.** Browsers can't set headers on the `WebSocket` API. The front sends two subprotocols: `bearer-token-carrier` and `quarkus-http-upgrade#Authorization#Bearer <jwt>` (URI-encoded). Quarkus `websockets-next` extracts the second one and injects `Authorization: Bearer <jwt>` before the OIDC handler runs — enabled by `quarkus.websockets-next.server.propagate-subprotocol-headers=true` and `quarkus.websockets-next.server.supported-subprotocols=bearer-token-carrier` in `application.properties`. The token never appears in the URI, so `/ws/*` access logs are safe to enable.

**CORS.** Armed globally — `quarkus.http.cors.enabled=true` is fixed at build time. Origin allowlist comes from `${CORS_ALLOWED_ORIGINS:https://qui-est-qui.lepgu.fr}` (defaults to the prod front domain; cross-origin requests from anywhere else are denied, including in dev/test where the front talks to the back same-origin anyway). Prod overrides via env. Methods `GET,POST,PATCH,DELETE,OPTIONS`, headers `Authorization,Content-Type`, exposed `Location`. Bearer-only auth → credentials stays false.

### Production deployment

VPS: Fedora 43 cloud (`fedora` sudoer, SSH key only, password & root login disabled, firewalld 22/80/443, `dnf5-automatic.timer` with `apply_updates=yes`, weekly OVH snapshots). Domain `lepgu.fr` (OVH); root reserved for a future portfolio — all app hostnames nested under `qui-est-qui.lepgu.fr`:

| Service | Public URL |
|---------|-----------|
| Frontend | `qui-est-qui.lepgu.fr` |
| Backend (REST + WS) | `api.qui-est-qui.lepgu.fr` |
| Keycloak | `auth.qui-est-qui.lepgu.fr` |
| MinIO | `s3.qui-est-qui.lepgu.fr` |

These names lock in the Caddyfile site blocks, `OIDC_AUTH_SERVER_URL` (`https://auth.qui-est-qui.lepgu.fr/realms/qui-est-ce`), Keycloak realm redirect URIs / web origins, CORS origins, and the frontend API base URL — keep them in sync if changed. Stack runs as Docker Compose (Caddy + back + Keycloak + Postgres + MinIO); see roadmap task 7.

### CI

Push to `main` triggers two workflows:

- `deploy-swagger.yml` — skips tests, builds, publishes OpenAPI to `Fileboss/qui-est-ce_back_API` (GitHub Pages). Needs `API_TOKEN_GITHUB`.
- `build-and-push.yml` — runs full tests, builds the JVM image via `src/main/docker/Dockerfile.jvm`, pushes to `ghcr.io/fileboss/qui-est-ce-back:{latest,sha-<short>}` via `GITHUB_TOKEN` (no PAT). Tests rely on LocalStack — pinned to `localstack/localstack:3` in `application.properties` because the `:latest` tag now requires a paid license. SSH-deploy-to-VPS step is not yet automated; updates are pulled manually (`docker compose pull back && docker compose up -d back`).

### Image URLs (prod)

`ImageService.getImageUrl()` reads optional `game.image.public-base-url` (env `S3_PUBLIC_BASE_URL`). When set, image links use that base instead of the internal `quarkus.s3.endpoint-override` — necessary because the in-cluster S3 endpoint (`http://minio:9000`) is unreachable from browsers and triggers mixed-content under HTTPS. Empty in dev/test → fallback to `s3.utilities().getUrl(...)` against LocalStack.
