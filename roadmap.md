# Roadmap

Backlog for taking the `qui-est-ce` backend from "works on my machine" to "running on a VPS my friends can use". Tasks are roughly ordered by dependency, not strict priority.

---

## 1. Two distinct players required to start ✅

Currently nothing prevents the same user from calling both `player1/join` and `player2/join`, which would let a single user start a game against themselves.

- Extract the caller's `sub` claim from the JWT on each join call and store it alongside the slot in `GameEngine` (no new entity needed — just two nullable `String` fields).
- Before `start`, verify `player1Sub` and `player2Sub` are both non-null and not equal. Throw `IllegalStateException` (→ 400) otherwise.
- If a user attempts to join a slot that is already taken by *them*, return 409.
- Update `GameUpdateEvent` to include how many players have joined so the frontend can show "waiting for player 2".
- Tests: same user joining both slots returns 409; starting with only one player joined returns 400; two different users can start normally.

**Done when:** a single authenticated user cannot occupy both player slots, and the game refuses to start until two distinct users have joined.

---

## 2. Database migrations (Flyway) ✅

Stop relying on `drop-and-create`. Required before any prod data exists.

- Add `quarkus-flyway` extension.
- Set `quarkus.hibernate-orm.database.generation=validate` for `%prod`.
- Generate baseline `V1__init.sql` from current schema.
- Enable `quarkus.flyway.migrate-at-start=true` for `%prod`.

**Done when:** restarting the app does not wipe the database, and adding a column requires a new `V<n>__*.sql` file.

---

## 3. Production configuration profile ✅

Externalize everything Dev Services currently provides.

- Add a `%prod` block in `application.properties` (or a separate `application-prod.properties`) for: `quarkus.datasource.*`, `quarkus.s3.*`, `quarkus.oidc.*`.
- All sensitive values read from env vars: `${DB_PASSWORD}`, `${S3_SECRET_KEY}`, `${OIDC_CLIENT_SECRET}`, etc.
- `quarkus.http.host=0.0.0.0`, `quarkus.http.port=8080`.
- Verify locally by running `./mvnw package` and starting the JAR with env vars pointed at local containers.

**Done when:** the JAR boots in `prod` mode against an external Postgres, MinIO, and Keycloak with no Dev Services involved.

---

## 4. Startup safety checks

Two small defensive behaviours needed before running against real infrastructure.

- **MinIO bucket:** add a `@Startup` (or `StartupEvent` observer) bean that calls `headBucket`; if 404, calls `createBucket`. Idempotent — safe on every boot.
- **Health checks:** add `quarkus-smallrye-health`. Verify `/q/health/live` and `/q/health/ready` respond. Readiness should fail if DB or MinIO are unreachable (Quarkus does most of this automatically once the extensions are in).

**Done when:** pointing the app at an empty MinIO still allows image upload on first request, and `curl /q/health/ready` returns 503 if Postgres is down.

---

## 5. Production Keycloak realm

The committed `realm-export.json` has `dev-secret`, wildcard redirect URIs, and three users with password `password`. Unfit for prod.

- Create a separate `realm-export-prod.json` (or set up the realm manually post-deploy) with: real client secret (env var, not committed), exact redirect URIs (`https://app.<domain>/*`), real `webOrigins`, no test users.
- Decide: import realm on Keycloak startup, or configure manually via Keycloak admin UI on first boot. Manual is simpler at this scale.
- Document the manual setup steps in README so it's reproducible.

**Done when:** prod Keycloak has zero hardcoded users, the client secret is in an env var, and redirect URIs are locked to the prod frontend domain.

---

## 6. Rent and configure VPS

Pick a provider and prepare the host. Plan for ~4GB RAM (Keycloak alone needs ~1GB).

- Pick a provider (Hetzner CX22 ~4€/month, Scaleway, OVH, etc.). 4GB RAM, 2 vCPU, 40GB SSD is comfortable.
- Pick a domain (or subdomain of an existing one). Configure DNS A records: `app`, `api`, `auth`, `s3` → VPS IP.
- Initial host setup: non-root user, SSH key only (disable password auth), UFW allowing 22/80/443, automatic security updates (`unattended-upgrades`).
- Install Docker + Docker Compose plugin.
- Enable weekly VPS snapshots in the provider's panel.

**Done when:** SSH-as-non-root works, `docker compose version` works, and the four DNS names resolve to the VPS.

---

## 7. Docker Compose stack

Single `docker-compose.yml` in an infra repo (or `infra/` folder) describing the full stack.

- Services: `caddy`, `front`, `back`, `keycloak`, `postgres`, `minio`.
- One `postgres` container with two databases: `qui_est_ce` and `keycloak`. Init script in `/docker-entrypoint-initdb.d/`.
- Named volumes for `postgres_data`, `minio_data`, `keycloak_data`, `caddy_data` (Let's Encrypt certs).
- Internal Docker network — only Caddy publishes ports 80/443.
- `restart: unless-stopped` on everything.
- All secrets in a `.env` file (gitignored), referenced as `${VAR}` in the compose file.
- Caddyfile: TLS auto for `app.`, `api.`, `auth.`, `s3.` — reverse proxy to the right internal service.

**Done when:** `docker compose up -d` on the VPS brings the whole stack up and `https://app.<domain>` loads the frontend.

---

## 8. CI/CD: build and push image, deploy to VPS

Right now CI only publishes OpenAPI docs. There's no path from `git push` to production.

- Add a workflow that on push to `main`: builds a Docker image, pushes to GHCR (`ghcr.io/fileboss/qui-est-ce-back:<sha>` and `:latest`).
- Decide: native image (smaller, slower build) or JVM image (faster build, more RAM). Start with JVM, switch to native if RAM gets tight.
- Deploy step: SSH to VPS, `docker compose pull && docker compose up -d back`. Use a deploy SSH key stored as a GitHub secret.
- Run tests in CI before building (currently skipped — re-enable now that the suite has been expanded).

**Done when:** pushing to `main` results in the new version running on the VPS within ~5 minutes, automatically.

---

## 9. WebSocket auth via subprotocol

Replace `?access_token=<jwt>` with the subprotocol approach to stop leaking tokens into logs.

- Frontend sends token as a WS subprotocol header (the only header browsers allow on WS).
- Replace `WebSocketTokenFilter` logic to read from the `Sec-WebSocket-Protocol` header instead of the query string.
- Update README docs.
- Coordinate with frontend change.

**Done when:** access logs no longer contain JWTs, and both `/ws/game/*` and `/ws/games` still authenticate correctly.

---

## 10. Prod HTTP hardening: CORS and body size limits

Two small config-level defences that should be explicit rather than relying on the reverse proxy.

- **CORS:** configure `quarkus.http.cors.origins=https://app.<domain>` for `%prod`. Allow only the methods/headers actually needed.
- **Body size:** configure `quarkus.http.limits.max-body-size` (e.g. 5MB) for image uploads. Add a max-image-size check in `ImageService` before writing to S3.
- Optional: rate limit at Caddy level (Caddy has a `rate_limit` module) on `/api/*` to avoid runaway loops from a buggy frontend.

**Done when:** a browser request from any non-prod origin is blocked, uploading a 100MB file is rejected with a clear error, and there is a documented per-IP request ceiling.

---

## 11. In-game live text chat

Players in a game room can send messages to each other in real time. Nothing is persisted — messages exist only as long as the WebSocket session lives.

- Reuse the existing `/ws/game/{gameId}` channel. Add a new inbound message type `CHAT_MESSAGE` with a `text` field (max ~500 chars, validated server-side).
- `GameWebSocket.@OnTextMessage` already receives raw text — parse the type field and dispatch accordingly. No new endpoint needed.
- Broadcast the message to all connections on that game path with a new outbound event type `CHAT_MESSAGE` containing `senderSub` (or display name from the JWT `preferred_username` claim) and `text`.
- Reject messages from unauthenticated or non-participant connections (same guard as guess/reset).
- No storage — on disconnect the history is gone.
- Tests: a message sent by player 1 is received by player 2; a non-participant connection does not receive game-private chat; oversized messages are rejected with a WS close frame or error event.

**Done when:** two players in the same game room can exchange text messages in real time, and nothing is written to the database.

---

## 12. Observability: structured logs and basic metrics

For when something breaks at 11pm.

- Add `quarkus-logging-json` — structured logs to stdout (Docker captures them).
- Add `quarkus-micrometer-registry-prometheus` — `/q/metrics` endpoint.
- Optional: a small Grafana + Prometheus container in the compose stack, or just `docker logs` for now and add Grafana later.
- Set sensible log levels for `%prod` (INFO root, WARN for noisy libs).

**Done when:** logs are queryable as JSON, and JVM/HTTP metrics are exposed on `/q/metrics`.

---

## 13. Backup strategy

Documented and automated, even at small scale.

- Cron on the VPS: nightly `pg_dump` of both databases to a local directory, then `rsync`/`rclone` to offsite storage (Backblaze B2, another VPS, or even a home NAS).
- Same for MinIO data (`mc mirror`).
- Retention: 7 daily, 4 weekly.
- Test the restore once — write down the exact commands in the README.

**Done when:** there is a documented, tested procedure to restore the app from a backup onto a fresh VPS.

---

## 14. Game state persistence

In-memory `GameRegistry` loses all state on restart.

- Persist active games to Postgres (a `game` table with the serialized state).
- Reload on startup.
- Decide on TTL — abandoned games should not accumulate forever.

**Done when:** restarting the backend mid-game does not lose the game.

---

## 15. User entity

- Add a `User` entity: `id`, `keycloak_sub` (unique, indexed), `display_name`, `avatar_url` (nullable), `created_at`, `updated_at`.
- Sync from JWT on first authenticated request — no manual signup. Pull `sub`, `preferred_username`, and `name` from the token; create the user row lazily.
- Endpoint `GET /users/me` — returns the current user's profile.
- Endpoint `PATCH /users/me` — lets the user edit `display_name` and `avatar_url`. Validate length, reject empty display names.
- Tests: editing `/users/me` only changes the caller's row; `display_name` uniqueness is *not* enforced (friends can clash; identity is the keycloak sub).

**Done when:** every authenticated user has a persistent profile row, and they can read and update it.

---

## 16. Pack and card ownership

- Add `owner_id` FK on `Pack` (cards inherit ownership through their pack).
- Repositories: list/get/create/update/delete scoped to the authenticated user. `admin` keeps full access.
- Migrate existing data: assign all current packs to the `admin` user in a Flyway migration.
- Tests: `player1` token cannot read or modify `player2`'s packs.

**Done when:** packs are visible only to their owner (and to `admin`).

---

## 17. Identity-based join endpoint (requires task 15)

Replace the two position-based join endpoints with a single identity-aware one.

- Add `player1_user_id` / `player2_user_id` FK columns to the game state, referencing the `User` table (Flyway migration).
- Replace `player1/join` and `player2/join` with a single `POST /games/{id}/join` that assigns the caller to the first free slot and returns the card they need to guess (keep the cross-assignment quirk).
- `POST /games/{id}/start` checks both slots are filled and the caller is one of them.
- Update `GameUpdateEvent` payloads to include joined users' display names.
- Tests: third user joining a full game gets 409; starting with one player returns 400.

**Done when:** the two position-based join endpoints are gone and a single join endpoint handles both players.

---

## 18. Identity-based guess and reset (requires task 17)

Complete the identity migration by replacing the remaining position-based action endpoints.

- Replace `player1/guess` and `player2/guess` with a single `POST /games/{id}/guess` — the engine derives which player from the JWT `sub`.
- Reset endpoint checks that the caller is a participant; non-participants get 403.
- Remove all now-dead position-based endpoints.
- Tests: non-participant trying to guess returns 403; the correct player guessing works as before.

**Done when:** all game actions (join, start, guess, reset) are tied to authenticated identity and no position-based endpoints remain.

---

## Notes

- Tasks 1–4 can happen on a feature branch before any infra exists.
- Tasks 5–6 are human ops steps (Keycloak realm + VPS setup).
- Tasks 7–8 are the "first deploy" milestone — once 8 is done, the loop is closed.
- Tasks 9–14 harden the prod environment and can be tackled incrementally after first deploy.
- Tasks 15–18 bring full user identity into the game model; they depend on task 2 (Flyway) being in place.
