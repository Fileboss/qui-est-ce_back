# Roadmap

Backlog for taking the `qui-est-ce` backend from "works on my machine" to "running on a VPS my friends can use". Tasks are roughly ordered by dependency, not strict priority.

---

## 1. Users, ownership, and identity-based game joining

Currently `Pack` and `Card` are global, and `GameEngine` exposes `player1/join` / `player2/join` as anonymous slots. Make ownership and game participation tied to real, authenticated users.

### 1a. User entity

- Add a `User` entity: `id`, `keycloak_sub` (unique, indexed), `display_name`, `avatar_url` (nullable), `created_at`, `updated_at`.
- Sync from JWT on first authenticated request — no manual signup. Pull `sub`, `preferred_username`, and `name` from the token; create the user row lazily.
- Endpoint `GET /users/me` — returns the current user's profile.
- Endpoint `PATCH /users/me` — lets the user edit `display_name` and `avatar_url`. Validate length, reject empty display names.
- Tests: editing `/users/me` only changes the caller's row; `display_name` uniqueness is *not* enforced (friends can clash; identity is the keycloak sub).

### 1b. Pack and card ownership

- Add `owner_id` FK on `Pack` (cards inherit ownership through their pack).
- Repositories: list/get/create/update/delete scoped to the authenticated user. `admin` keeps full access.
- Migrate existing data: assign all current packs to the `admin` user in the Flyway baseline.
- Tests: `player1` token cannot read or modify `player2`'s packs.

### 1c. Identity-based game joining

Replace the position-based `player1/join` / `player2/join` model with identity-based joining.

- A `Game` tracks `player1_user_id` and `player2_user_id` (both nullable until joined).
- New flow:
  - `POST /games` — creates a game in `PREPARING`. Caller is *not* automatically a player.
  - `POST /games/{id}/join` — assigns the caller to the first free slot. Returns the card they need to guess (the opponent's target — keep the existing cross-assignment quirk).
  - `POST /games/{id}/start` — only succeeds if both `player1_user_id` and `player2_user_id` are set, and the caller is one of them.
- **Same user cannot occupy both slots.** If a user calls `join` twice on the same game, return 409 (or 200 with their existing slot — pick one and document it).
- Guess and reset endpoints check that the caller is the relevant player. `player1/guess` becomes `games/{id}/guess` and the engine derives which player from the JWT.
- Update `GameUpdateEvent` payloads to include the joined users' display names so the frontend can show "waiting for player 2".
- Tests: a third user trying to join a full game gets 409; starting with only one player joined returns 400; a non-participant trying to guess returns 403.

**Done when:** two distinct authenticated users can join a game, the game cannot start until both have joined, no user can take both slots, and packs are visible only to their owner.

---

## 2. Database migrations (Flyway)

Stop relying on `drop-and-create`. Required before any prod data exists.

- Add `quarkus-flyway` extension.
- Set `quarkus.hibernate-orm.database.generation=validate` for `%prod`.
- Generate baseline `V1__init.sql` from current schema (after task 1 — so users, ownership, and game participation are in V1).
- Enable `quarkus.flyway.migrate-at-start=true` for `%prod`.

**Done when:** restarting the app does not wipe the database, and adding a column requires a new `V<n>__*.sql` file.

---

## 3. Production configuration profile

Externalize everything Dev Services currently provides.

- Add a `%prod` block in `application.properties` (or a separate `application-prod.properties`) for: `quarkus.datasource.*`, `quarkus.s3.*`, `quarkus.oidc.*`.
- All sensitive values read from env vars: `${DB_PASSWORD}`, `${S3_SECRET_KEY}`, `${OIDC_CLIENT_SECRET}`, etc.
- `quarkus.http.host=0.0.0.0`, `quarkus.http.port=8080`.
- Verify locally by running `./mvnw package` and starting the JAR with env vars pointed at local containers.

**Done when:** the JAR boots in `prod` mode against an external Postgres, MinIO, and Keycloak with no Dev Services involved.

---

## 4. MinIO bucket auto-creation on startup

`ImageService` assumes `game-images` exists. It won't on a fresh MinIO.

- Add a `@Startup` (or `StartupEvent` observer) bean that calls `headBucket`; if 404, calls `createBucket`.
- Idempotent — safe to run on every boot.

**Done when:** pointing the app at an empty MinIO instance still allows image upload on first request.

---

## 5. Health checks

For container restart policies and uptime monitoring.

- Add `quarkus-smallrye-health`.
- Verify `/q/health/live` and `/q/health/ready` respond.
- Readiness should fail if DB or MinIO are unreachable (Quarkus does most of this automatically once the extensions are in).

**Done when:** `curl /q/health/ready` returns 503 if Postgres is down, 200 otherwise.

---

## 6. Production Keycloak realm

The committed `realm-export.json` has `dev-secret`, wildcard redirect URIs, and three users with password `password`. Unfit for prod.

- Create a separate `realm-export-prod.json` (or set up the realm manually post-deploy) with: real client secret (env var, not committed), exact redirect URIs (`https://app.<domain>/*`), real `webOrigins`, no test users.
- Decide: import realm on Keycloak startup, or configure manually via Keycloak admin UI on first boot. Manual is simpler at this scale.
- Document the manual setup steps in README so it's reproducible.

**Done when:** prod Keycloak has zero hardcoded users, the client secret is in an env var, and redirect URIs are locked to the prod frontend domain.

---

## 7. Rent and configure VPS

Pick a provider and prepare the host. Plan for ~4GB RAM (Keycloak alone needs ~1GB).

- Pick a provider (Hetzner CX22 ~4€/month, Scaleway, OVH, etc.). 4GB RAM, 2 vCPU, 40GB SSD is comfortable.
- Pick a domain (or subdomain of an existing one). Configure DNS A records: `app`, `api`, `auth`, `s3` → VPS IP.
- Initial host setup: non-root user, SSH key only (disable password auth), UFW allowing 22/80/443, automatic security updates (`unattended-upgrades`).
- Install Docker + Docker Compose plugin.
- Enable weekly VPS snapshots in the provider's panel.

**Done when:** SSH-as-non-root works, `docker compose version` works, and the four DNS names resolve to the VPS.

---

## 8. Docker Compose stack

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

## 9. CI/CD: build and push image, deploy to VPS

Right now CI only publishes OpenAPI docs. There's no path from `git push` to production.

- Add a workflow that on push to `main`: builds a Docker image, pushes to GHCR (`ghcr.io/fileboss/qui-est-ce-back:<sha>` and `:latest`).
- Decide: native image (smaller, slower build) or JVM image (faster build, more RAM). Start with JVM, switch to native if RAM gets tight.
- Deploy step: SSH to VPS, `docker compose pull && docker compose up -d back`. Use a deploy SSH key stored as a GitHub secret.
- Run tests in CI before building (currently skipped — re-enable now that the suite has been expanded).

**Done when:** pushing to `main` results in the new version running on the VPS within ~5 minutes, automatically.

---

## 10. WebSocket auth via subprotocol

Replace `?access_token=<jwt>` with the subprotocol approach to stop leaking tokens into logs.

- Frontend sends token as a WS subprotocol header (the only header browsers allow on WS).
- Replace `WebSocketTokenFilter` logic to read from the `Sec-WebSocket-Protocol` header instead of the query string.
- Update README docs.
- Coordinate with frontend change.

**Done when:** access logs no longer contain JWTs, and both `/ws/game/*` and `/ws/games` still authenticate correctly.

---

## 11. CORS configuration

Explicit, not relying on the reverse proxy.

- Configure `quarkus.http.cors.origins=https://app.<domain>` for `%prod`.
- Allow only the methods/headers actually needed.

**Done when:** the frontend can call the backend from the prod domain, and a browser request from any other origin is blocked.

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

## 14. Upload size limits and basic rate limiting

Defensive, prevents accidental disk fill.

- Configure `quarkus.http.limits.max-body-size` (e.g. 5MB) for image uploads.
- Add a max-image-size check in `ImageService` before writing to S3.
- Optional: rate limit at Caddy level (Caddy has a `rate_limit` module) on `/api/*` to avoid runaway loops from a buggy frontend.

**Done when:** uploading a 100MB file is rejected with a clear error, and there is a documented per-IP request ceiling.

---

## 15. Game state persistence

In-memory `GameRegistry` loses all state on restart.

- Persist active games to Postgres (a `game` table with the serialized state, or a proper schema referencing the `User` rows from task 1).
- Reload on startup.
- Decide on TTL — abandoned games should not accumulate forever.

**Done when:** restarting the backend mid-game does not lose the game.

---

## Notes

- Tasks 1–6 can happen on a feature branch before any infra exists.
- Tasks 7–9 are the "first deploy" milestone — once 9 is done, the loop is closed.
- Tasks 10–14 harden the prod environment and can be tackled incrementally after first deploy.
- Task 15 is the only one that affects user-visible behavior; rest are invisible if everything works.
