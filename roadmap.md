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

## 4. Startup safety checks ✅

Two small defensive behaviours needed before running against real infrastructure.

- **MinIO bucket:** add a `@Startup` (or `StartupEvent` observer) bean that calls `headBucket`; if 404, calls `createBucket`. Idempotent — safe on every boot.
- **Health checks:** add `quarkus-smallrye-health`. Verify `/q/health/live` and `/q/health/ready` respond. Readiness should fail if DB or MinIO are unreachable (Quarkus does most of this automatically once the extensions are in).

**Done when:** pointing the app at an empty MinIO still allows image upload on first request, and `curl /q/health/ready` returns 503 if Postgres is down.

---

## 5. Production Keycloak realm ✅

The committed `realm-export.json` has `dev-secret`, wildcard redirect URIs, and three users with password `password`. Unfit for prod.

- Create a separate `realm-export-prod.json` with: client-secret placeholder (real value injected via env var), exact redirect URIs (`https://qui-est-qui.lepgu.fr/*`), real `webOrigins`, no test users.
- Import the realm on Keycloak startup via `--import-realm` (wired in the Compose stack — task 7). Imports run only on first boot; subsequent restarts skip silently.
- Document the post-import manual steps in README (regenerate client secret, set `.env`, create first users) so the deployment is reproducible.

**Done when:** prod Keycloak has zero hardcoded users, the client secret is in an env var, and redirect URIs are locked to the prod frontend domain.

> *Post-review amendment (privilege separation).* The single `qui-est-ce-back` client was split into two: `qui-est-ce-back` (token validation only, no service-account roles) and `qui-est-ce-admin` (service-account holding `realm-management/manage-users` for `POST /admin/users`). Both secrets are resolved by Keycloak from `${OIDC_CLIENT_SECRET}` and `${KEYCLOAK_ADMIN_SECRET}` at realm-import time — no manual admin-UI regen step. The backend's `SecretGuard` (`src/main/java/admin/SecretGuard.java`) fails fast on empty, placeholder, or duplicated values. See README "🔐 Configuration Keycloak en production" for the full first-deploy walkthrough.

---

## 6. Rent and configure VPS ✅

Pick a provider and prepare the host. Plan for ~4GB RAM (Keycloak alone needs ~1GB).

- Pick a provider (Hetzner CX22 ~4€/month, Scaleway, OVH, etc.). 4GB RAM, 2 vCPU, 40GB SSD is comfortable.
- Pick a domain (or subdomain of an existing one). Configure DNS A records: `app`, `api`, `auth`, `s3` → VPS IP.
- Initial host setup: non-root user, SSH key only (disable password auth), UFW allowing 22/80/443, automatic security updates (`unattended-upgrades`).
- Install Docker + Docker Compose plugin.
- Enable weekly VPS snapshots in the provider's panel.

**Done when:** SSH-as-non-root works, `docker compose version` works, and the four DNS names resolve to the VPS.

---

## 7. Docker Compose stack ✅

Single `docker-compose.yml` in an infra repo (or `infra/` folder) describing the full stack.

- Services: `caddy`, `front`, `back`, `keycloak`, `postgres`, `minio`.
- One `postgres` container with two databases: `qui_est_ce` and `keycloak`. Init script in `/docker-entrypoint-initdb.d/`.
- Named volumes for `postgres_data`, `minio_data`, `keycloak_data`, `caddy_data` (Let's Encrypt certs).
- Internal Docker network — only Caddy publishes ports 80/443.
- `restart: unless-stopped` on everything.
- All secrets in a `.env` file (gitignored), referenced as `${VAR}` in the compose file.
- Caddyfile: TLS auto for `qui-est-qui.lepgu.fr`, `api.qui-est-qui.lepgu.fr`, `auth.qui-est-qui.lepgu.fr`, `s3.qui-est-qui.lepgu.fr` — reverse proxy to the right internal service.

**Done when:** `docker compose up -d` on the VPS brings the whole stack up and `https://qui-est-qui.lepgu.fr` loads the frontend.

---

## 8. Admin-only user creation endpoint ✅

Minimum viable user management for the first deploy — avoid sending people through the Keycloak admin console to create accounts. Self-registration, email verification, and password reset come later (separate task).

- The first admin user is created manually via the Keycloak admin console once the stack is up (already documented in the infra repo's first-boot README).
- Add `POST /admin/users` — body `{ username, password, role }` where `role` is `player` or `admin`. Username 3–30 chars, password ≥ 12 chars; reject duplicates with 409 and weak input with 400.
- Endpoint guarded by `@RolesAllowed("admin")`. Non-admins get 403.
- Implementation calls the Keycloak Admin REST API: create user, set credentials (non-temporary), assign realm role. The `qui-est-ce-back` client already has `serviceAccountsEnabled: true` — grant its service account the `realm-management` client role `manage-users` in `realm-export-prod.json` so it can manage the realm without an extra admin login.
- No `User` entity yet — that comes in task 16. The Keycloak account is enough; the back's profile row materializes lazily on first authenticated request once that task lands.
- Tests: `player` token → 403; `admin` token + valid body → 201; duplicate username → 409; weak password → 400.

**Done when:** an admin can create new accounts via the API without touching the Keycloak admin console, and those accounts can immediately log in via the frontend.

---

## 9. CI/CD: build and push image, deploy to VPS ✅ (build half) / ⏳ (deploy half)

Right now CI only publishes OpenAPI docs. There's no path from `git push` to production.

- Add a workflow that on push to `main`: builds a Docker image, pushes to GHCR (`ghcr.io/fileboss/qui-est-ce-back:<sha>` and `:latest`). ✅
- Decide: native image (smaller, slower build) or JVM image (faster build, more RAM). Start with JVM, switch to native if RAM gets tight. ✅ (JVM, via `src/main/docker/Dockerfile.jvm`)
- Deploy step: SSH to VPS, `docker compose pull && docker compose up -d back`. Use a deploy SSH key stored as a GitHub secret. ⏳ (still manual: `git pull && docker compose pull && docker compose up -d` on the VPS)
- Run tests in CI before building (currently skipped — re-enable now that the suite has been expanded). ✅

**Done when:** pushing to `main` results in the new version running on the VPS within ~5 minutes, automatically.

> *Status (2026-05-11).* First production deploy is live on `qui-est-qui.lepgu.fr`. The `qui-est-ce_back` and `qui-est-ce_front` workflows build, test, and push images to GHCR on every push to `main`. The SSH deploy step is still pending — for now, updates are applied manually on the VPS. Several non-roadmap fixes were needed along the way and are worth flagging:
>
> - **Back:** `LocalStack:latest` was switched to a pro-licensed image; pinned `quarkus.aws.devservices.localstack.image-name=localstack/localstack:3`. Image URLs returned to the browser now derive from `S3_PUBLIC_BASE_URL` (e.g. `https://s3.qui-est-qui.lepgu.fr`) instead of the internal `http://minio:9000` endpoint.
> - **Infra:** Realm export now defines a separate `qui-est-ce-admin` client (M2M, `manage-users`); both client secrets resolve at realm-import time via `${OIDC_CLIENT_SECRET}` / `${KEYCLOAK_ADMIN_SECRET}` substitution against the Keycloak container env. Keycloak runs without `--optimized` so the build-time options apply at startup. The Keycloak healthcheck now uses HTTP/1.0 to force `Connection: close`. Caddy routes `/api/*` and `/ws/*` on the front domain to the back so the browser sees one origin, mirroring the dev `proxy.conf.json` (with `handle_path` stripping `/api`).
> - **Front:** Keycloak URL split into `environment.ts` / `environment.prod.ts` with Angular `fileReplacements`; prod bundle points at `https://auth.qui-est-qui.lepgu.fr`.

---

## 10. WebSocket auth via subprotocol ✅

Replaced `?access_token=<jwt>` with the `Sec-WebSocket-Protocol` subprotocol approach to stop leaking tokens into logs.

- Frontend sends two subprotocols: `bearer-token-carrier` (a fixed carrier name the server echoes back) and `quarkus-http-upgrade#Authorization#Bearer <token>` (URI-encoded). ✅ (back ready)
- Enabled `quarkus.websockets-next.server.propagate-subprotocol-headers=true` and `quarkus.websockets-next.server.supported-subprotocols=bearer-token-carrier`. Deleted `WebSocketTokenFilter` and dropped the `quarkus-reactive-routes` dependency. ✅
- Updated README and CLAUDE.md. ✅
- Frontend change still required — the back deploy on its own will break existing WS clients. ⏳

**Done when:** access logs no longer contain JWTs, and both `/ws/game/*` and `/ws/games` still authenticate correctly.

---

## 11. Prod HTTP hardening: CORS and body size limits ✅

Two small config-level defences that should be explicit rather than relying on the reverse proxy.

- **Body size** — already enforced before this task: `quarkus.http.limits.max-body-size=5M` is global, and `CardResource.createCard` checks `form.image.size() > MAX_IMAGE_BYTES` and throws `BadRequestException`. The constant moved to `ImageService.MAX_IMAGE_BYTES` so it lives next to the S3 boundary; `CardResource` now references it. Oversize coverage is `CardResourceTest.createCard_returns400Or413_whenImageTooLarge`. ✅
- **CORS** — armed globally (`quarkus.http.cors.enabled=true` is build-time-fixed). Origin allowlist comes from `${CORS_ALLOWED_ORIGINS:https://qui-est-qui.lepgu.fr}` — defaults to the prod front domain, prod overrides via env. Dev/test inherit the same default but are unaffected because the front talks to the back same-origin via the Angular proxy / Caddy path-routing. Methods limited to `GET,POST,PATCH,DELETE,OPTIONS`; allowed headers `Authorization,Content-Type`; `Location` exposed. Credentials stays false — bearer-only auth. Coverage: `CorsPreflightTest`. ✅
- **Rate limit at Caddy** — promoted out of this task into roadmap task 20. Requires `mholt/caddy-ratelimit` + a custom Caddy build via xcaddy, and is low priority for the current friends-only audience.

**Done when:** a browser request from a non-allowlisted origin sees no `Access-Control-Allow-Origin` header, an oversize upload is rejected with a clear error, and the rate-limit follow-up is tracked separately.

---

## 12. Admin user management — auto-generated passwords, list, reset, delete ✅

Sharpen the admin flow from task 8. Today the admin types both username *and* password into the form, which leaks the credential through the browser/clipboard and is error-prone. Keycloak also requires email + first/last name on user creation by default — unnecessary friction for a friends-only game.

- `POST /admin/users` — drop `password` from the request body. The server generates a strong random password (≥ 16 chars, mixed case + digits + symbols) and returns it once in the 201 response so the admin can hand it off out-of-band. `UPDATE_PASSWORD` is already a required action, so the user is forced to rotate it on first login.
- `GET /admin/users` — list realm users (`id`, `username`, `enabled`, `createdTimestamp`, realm roles). Pass through Keycloak's `first`/`max` pagination params; cap `max` at 100.
- `POST /admin/users/{id}/reset-password` — generate a fresh random password, re-arm `UPDATE_PASSWORD`, return the new password in the response. Old credentials stop working immediately.
- `DELETE /admin/users/{id}` — delete the user from Keycloak. Refuse to delete the caller's own account (400) to avoid an admin locking themselves out.
- Realm export: turn off the email / firstName / lastName requirements in the user-profile config so the admin endpoint and the Keycloak account-console screens don't demand them. Username + password is enough.
- All four endpoints stay behind `@RolesAllowed("admin")`; non-admins get 403.
- Tests: non-admin → 403 on each endpoint; create returns 201 with a non-empty `generatedPassword` field and the account can log in with it (then is forced through `UPDATE_PASSWORD`); list includes the freshly created user and respects `first`/`max`; reset returns a new password and the previous one is rejected by Keycloak; delete removes the user, second delete returns 404, self-delete returns 400.

**Done when:** admins can create (with auto-generated password), list, reset, and delete user accounts via the API without opening the Keycloak admin console, and new users are not prompted for email or name on first login.

---

## 13. Pagination (slicing) across list endpoints

Several list endpoints currently return everything in one shot. Cheap today, but a foot-gun once a real user has hundreds of packs or the admin user list grows. Standardize on a simple offset/limit slice everywhere it's useful, so the front can lazy-load and the back is not forced to renegotiate the contract later.

- Single convention: `?first=<int>&max=<int>` (mirrors the Keycloak admin API used in task 12). `first` defaults to 0, `max` defaults to 20, hard-capped at 100. Negative values → 400.
- Return a small wrapper `{ items, first, max, total }` so the front can render "N of M". `total` runs a `count()` query — fine for current volumes.
- Endpoints to slice:
  - `GET /packs` (pack picker)
  - `GET /packs/{id}/cards` (when a pack is opened in the editor)
  - `GET /games` (lobby — currently returns every game regardless of state)
  - `GET /admin/users` (introduced in task 12 — return the same wrapper rather than the raw Keycloak list)
- Default ordering must be deterministic (e.g. `createdAt DESC, id DESC`) so pages don't shuffle between calls.
- Tests per endpoint: default page returns up to `max` items in stable order; `first=<n>` skips correctly; `max>100` is clamped; negative values → 400; `total` matches an unpaginated count.

**Done when:** every list endpoint accepts `first` / `max`, returns a `{ items, first, max, total }` wrapper with stable ordering, and the front can scroll a multi-page list without the server returning the full set.

---

## 14. In-game live text chat

Players in a game room can send messages to each other in real time. Nothing is persisted — messages exist only as long as the WebSocket session lives.

- Reuse the existing `/ws/game/{gameId}` channel. Add a new inbound message type `CHAT_MESSAGE` with a `text` field (max ~500 chars, validated server-side).
- `GameWebSocket.@OnTextMessage` already receives raw text — parse the type field and dispatch accordingly. No new endpoint needed.
- Broadcast the message to all connections on that game path with a new outbound event type `CHAT_MESSAGE` containing `senderSub` (or display name from the JWT `preferred_username` claim) and `text`.
- Reject messages from unauthenticated or non-participant connections (same guard as guess/reset).
- No storage — on disconnect the history is gone.
- Tests: a message sent by player 1 is received by player 2; a non-participant connection does not receive game-private chat; oversized messages are rejected with a WS close frame or error event.

**Done when:** two players in the same game room can exchange text messages in real time, and nothing is written to the database.

---

## 15. Observability: structured logs and basic metrics

For when something breaks at 11pm.

- Add `quarkus-logging-json` — structured logs to stdout (Docker captures them).
- Add `quarkus-micrometer-registry-prometheus` — `/q/metrics` endpoint.
- Optional: a small Grafana + Prometheus container in the compose stack, or just `docker logs` for now and add Grafana later.
- Set sensible log levels for `%prod` (INFO root, WARN for noisy libs).

**Done when:** logs are queryable as JSON, and JVM/HTTP metrics are exposed on `/q/metrics`.

---

## 16. Backup strategy

Documented and automated, even at small scale.

- Cron on the VPS: nightly `pg_dump` of both databases to a local directory, then `rsync`/`rclone` to offsite storage (Backblaze B2, another VPS, or even a home NAS).
- Same for MinIO data (`mc mirror`).
- Retention: 7 daily, 4 weekly.
- Test the restore once — write down the exact commands in the README.

**Done when:** there is a documented, tested procedure to restore the app from a backup onto a fresh VPS.

---

## 17. Game state persistence

In-memory `GameRegistry` loses all state on restart.

- Persist active games to Postgres (a `game` table with the serialized state).
- Reload on startup.
- Decide on TTL — abandoned games should not accumulate forever.

**Done when:** restarting the backend mid-game does not lose the game.

---

## 18. User entity

- Add a `User` entity: `id`, `keycloak_sub` (unique, indexed), `display_name`, `avatar_url` (nullable), `created_at`, `updated_at`.
- Sync from JWT on first authenticated request — no manual signup. Pull `sub`, `preferred_username`, and `name` from the token; create the user row lazily.
- Endpoint `GET /users/me` — returns the current user's profile.
- Endpoint `PATCH /users/me` — lets the user edit `display_name` and `avatar_url`. Validate length, reject empty display names.
- Tests: editing `/users/me` only changes the caller's row; `display_name` uniqueness is *not* enforced (friends can clash; identity is the keycloak sub).

**Done when:** every authenticated user has a persistent profile row, and they can read and update it.

---

## 19. Pack and card ownership

- Add `owner_id` FK on `Pack` (cards inherit ownership through their pack).
- Repositories: list/get/create/update/delete scoped to the authenticated user. `admin` keeps full access.
- Migrate existing data: assign all current packs to the `admin` user in a Flyway migration.
- Tests: `player1` token cannot read or modify `player2`'s packs.

**Done when:** packs are visible only to their owner (and to `admin`).

---

## 20. Identity-based join endpoint (requires task 18)

Replace the two position-based join endpoints with a single identity-aware one.

- Add `player1_user_id` / `player2_user_id` FK columns to the game state, referencing the `User` table (Flyway migration).
- Replace `player1/join` and `player2/join` with a single `POST /games/{id}/join` that assigns the caller to the first free slot and returns the card they need to guess (keep the cross-assignment quirk).
- `POST /games/{id}/start` checks both slots are filled and the caller is one of them.
- Update `GameUpdateEvent` payloads to include joined users' display names.
- Tests: third user joining a full game gets 409; starting with one player returns 400.

**Done when:** the two position-based join endpoints are gone and a single join endpoint handles both players.

---

## 21. Identity-based guess and reset (requires task 20)

Complete the identity migration by replacing the remaining position-based action endpoints.

- Replace `player1/guess` and `player2/guess` with a single `POST /games/{id}/guess` — the engine derives which player from the JWT `sub`.
- Reset endpoint checks that the caller is a participant; non-participants get 403.
- Remove all now-dead position-based endpoints.
- Tests: non-participant trying to guess returns 403; the correct player guessing works as before.

**Done when:** all game actions (join, start, guess, reset) are tied to authenticated identity and no position-based endpoints remain.

---

## 22. Rate-limit `/api/*` at the reverse proxy

Defensive layer against runaway-loop clients and brute-force probing. Low priority for the current friends-only audience but worth doing once observability (task 15) is in place so spikes are visible. Promoted out of task 11.

- Caddy core does not ship a `rate_limit` directive — requires the `mholt/caddy-ratelimit` community plugin + a custom Caddy build via xcaddy. Bake the xcaddy step into the infra repo's image build.
- Two zones at minimum: a per-IP cap on `/api/*` (e.g. 60 req/min) and a tighter one on any auth-adjacent paths.
- Surface 429s in metrics (Prometheus counter on the back, or Caddy's own metrics) so persistent throttling is visible.
- Document the limits and how to tune them in the infra README.

**Done when:** a script hammering `/api/*` from one IP gets 429 after the documented threshold and the event is visible in metrics.

---

## Notes

- Tasks 1–4 can happen on a feature branch before any infra exists.
- Tasks 5–6 are human ops steps (Keycloak realm + VPS setup).
- Tasks 7–9 are the "first deploy" milestone — once 9 is done, the loop is closed (8 unblocks real users without manual Keycloak admin clicks).
- Tasks 10–17 harden the prod environment and can be tackled incrementally after first deploy.
- Tasks 18–21 bring full user identity into the game model; they depend on task 2 (Flyway) being in place.
- Task 22 is post-MVP operational polish — defer until observability (15) is in place.
