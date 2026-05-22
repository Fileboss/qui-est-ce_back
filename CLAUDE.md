# CLAUDE.md

## Commands

```bash
./mvnw compile quarkus:dev          # Dev mode (needs Docker)
./mvnw test                         # All tests
./mvnw test -Dtest=GameEngineTest   # Single test class
./mvnw clean package -DskipTests    # Build → OpenAPI at target/generated/swagger/
./mvnw quarkus:add-extension -Dextensions="<name>"
```

Dev Services auto-provision PostgreSQL, MinIO (`game-images` bucket) and Keycloak via Testcontainers. The dev DB is reused between restarts (`%dev.quarkus.datasource.devservices.reuse=true`). Schema is owned by **Flyway** (`db/migration/`) — never let Hibernate generate DDL. Prod reads all infra coordinates from env vars (see `.env.example`, run with `QUARKUS_PROFILE=prod`). Code must be SonarQube-clean. Roadmap: `roadmap.md`.

## Architecture

REST + WebSocket backend for a 2-player "Guess Who" game. In-memory; one `GameEngine` per game id.

### Packages

- **`game`** — `GameEngine` (state machine, no persistence), `GameRegistry` (`@ApplicationScoped` map + event firing), `GameResource` (REST), `GameWebSocket` (`/ws/game/{gameId}`), `GamesWebSocket` (`/ws/games`), `GameUpdateBroadcaster` (CDI observer → broadcast).
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

**Player identity.** Each join records the caller's principal name in `player1Sub` / `player2Sub`. If the same sub tries to take the other slot, `player1Join`/`player2Join` throws `PlayerConflictException` → 409. Re-joining the same slot is idempotent. `start()` requires both subs non-null. `GameResource` injects `SecurityIdentity` and passes `identity.getPrincipal().getName()` through. `GameEngine.isParticipant(sub)` is used by the chat handler to gate inbound messages.

**Cross-assignment quirk** (lives inside `GameEngine`): `player1Join` returns `player2CardDTOToGuess` and vice versa — each join returns the card the *other* player must guess. Intentional.

### WebSocket

| Endpoint | Purpose |
|----------|---------|
| `ws://…/ws/game/{gameId}` | Per-game state transitions, joins, guess results, chat |
| `ws://…/ws/games` | Lobby: game created/deleted |

- `@OnOpen` pushes current state to the connecting client. Subsequent state pushes go through `GameUpdateBroadcaster` via `OpenConnections`.
- **`GameUpdateBroadcaster`** observes `@Observes GameUpdateEvent` (sync) and filters connections by path param. Events are fired with `Event.fire(...)` from both `GameRegistry` and `GameWebSocket`. `CHAT_ERROR` bypasses the broadcaster entirely — written directly to the offending `WebSocketConnection`.
- **`GameUpdateEvent`** — record `(gameId, type, gameState, correct, playersJoined, senderSub, senderName, text)`. `@JsonInclude(NON_NULL)` strips null fields. `playersJoined` set on join events only; `senderSub`/`senderName`/`text` set on chat events only.

Events:

| Trigger | `type` | Channel |
|---------|--------|---------|
| `createGame` | `GAME_CREATED` | `/ws/games` |
| `player1Join`, `player2Join`, `startGame`, guess, `resetGame` | `STATE_CHANGE` | `/ws/game/{gameId}` |
| `removeGame` | `DELETED` | both |
| Valid inbound `CHAT_MESSAGE` from a participant | `CHAT_MESSAGE` | `/ws/game/{gameId}` (broadcast, sender included) |
| Invalid chat (non-participant, blank, > 500 chars, malformed JSON, unknown type, missing game) | `CHAT_ERROR` | offending connection only |

**Chat.** `GameWebSocket.@OnTextMessage` parses `{"type":"CHAT_MESSAGE","text":"…"}`, validates participant + non-blank + ≤ `MAX_CHAT_TEXT` (500), and either fires a `CHAT_MESSAGE` event (routed through the broadcaster's `default` branch) or writes a `CHAT_ERROR` frame back via `connection.sendTextAndAwait`. `senderSub` is `identity.getPrincipal().getName()` — under default Quarkus OIDC config that's the `preferred_username` claim, not the JWT `sub` UUID. `senderName` reads `preferred_username` explicitly from the `JsonWebToken`, falling back to `senderSub`. No persistence — history dies with the WS session.

### DTOs & misc

`CardDTO`, `GameStatusResponse`, `GameUpdateEvent` are records. Entities/services use Lombok (`@RequiredArgsConstructor`, `@Getter`, …).

**Hibernate naming.** Quarkus 3 uses Hibernate's default passthrough `PhysicalNamingStrategyStandardImpl` — no camelCase→snake_case conversion. `imageUrl` maps to `imageurl` in PostgreSQL (lowercased by the driver, no underscore). Reflect this in any new Flyway migration.

### Security

OIDC + Keycloak (`quarkus-oidc`). Dev Services auto-provision Keycloak and import `realm-export.json` (realm: `qui-est-ce`).

| Role | Permissions |
|------|------------|
| `player` | Read all; create/join/start/guess/reset/delete games; chat |
| `admin` | `player` + manage packs/cards (CRUD) + manage users |

Test users (password `password`): `player1`, `player2` (player), `admin` (admin). Dev realm has `directAccessGrantsEnabled: true` on `qui-est-ce-back` so tests can mint user JWTs via password grant — prod realm keeps it explicitly `false`.

**WebSocket auth.** Browsers can't set headers on the `WebSocket` API. The front sends two subprotocols: `bearer-token-carrier` and `quarkus-http-upgrade#Authorization#Bearer <jwt>` (URI-encoded). Quarkus `websockets-next` injects `Authorization: Bearer <jwt>` before the OIDC handler runs (enabled by `quarkus.websockets-next.server.propagate-subprotocol-headers=true` + `…supported-subprotocols=bearer-token-carrier`). Token never appears in the URI; `/ws/*` access logs are safe to enable.

**CORS.** Armed globally (`quarkus.http.cors.enabled=true` is build-time-fixed). Origin allowlist from `${CORS_ALLOWED_ORIGINS:https://qui-est-qui.lepgu.fr}` — dev/test inherit the prod default but are unaffected because the front talks to the back same-origin. Methods `GET,POST,PATCH,DELETE,OPTIONS`, headers `Authorization,Content-Type`, exposed `Location`. Bearer-only → credentials stays false.

### Production deployment

VPS (Fedora 43 cloud, OVH) running a Docker Compose stack: Caddy + back + Keycloak + Postgres + MinIO. Domain `lepgu.fr`; all hostnames under `qui-est-qui.lepgu.fr`:

| Service | Public URL |
|---------|-----------|
| Frontend | `qui-est-qui.lepgu.fr` |
| Backend (REST + WS) | `api.qui-est-qui.lepgu.fr` |
| Keycloak | `auth.qui-est-qui.lepgu.fr` |
| MinIO | `s3.qui-est-qui.lepgu.fr` |

These names lock in Caddyfile site blocks, `OIDC_AUTH_SERVER_URL`, Keycloak realm redirect URIs / web origins, CORS origins, and the front's API base — keep them in sync if changed.

### CI

Two workflows on push to `main`:

- `deploy-swagger.yml` — skips tests, publishes OpenAPI to `Fileboss/qui-est-ce_back_API` (GH Pages). Needs `API_TOKEN_GITHUB`.
- `build-and-push.yml` — full tests, JVM image via `src/main/docker/Dockerfile.jvm`, push to `ghcr.io/fileboss/qui-est-ce-back:{latest,sha-<short>}` via `GITHUB_TOKEN`. Tests pin `localstack/localstack:3` (the `:latest` tag now requires a paid license). SSH-deploy step still manual (`docker compose pull back && docker compose up -d back`).

### Image URLs (prod)

`ImageService.getImageUrl()` reads optional `game.image.public-base-url` (env `S3_PUBLIC_BASE_URL`). When set, image links use that base instead of the internal `quarkus.s3.endpoint-override` — the in-cluster `http://minio:9000` is unreachable from browsers and triggers mixed-content under HTTPS. Empty in dev/test → fallback to `s3.utilities().getUrl(...)` against LocalStack.
