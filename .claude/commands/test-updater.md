---
description: Find genuine test gaps in this Quarkus repo and add high-signal tests (no shallow mocks, no coverage padding).
argument-hint: "[optional scope: class name, package, or file path]"
---

# Test Updater — Quarkus / Java 21

Scope: `$ARGUMENTS` (if empty, audit the whole `src/main/java` tree against `src/test/java`).

You are adding tests to a production Quarkus 3.x backend. The bar is **senior Java engineer in 2026**. The goal is *catching real regressions*, not raising a coverage number.

## Hard rules

1. **Find the gap before writing anything.** Start by listing untested classes, untested public methods, and untested branches/edge cases in already-tested classes. Present this list to the user and ask which to tackle — do not write tests in the first turn unless the scope argument is a single specific target.
2. **Justify every test.** For each new test, state in one line: *what regression would this catch that nothing else catches?* If the answer is "nothing concrete," drop the test.
3. **No shallow tests.** Forbidden:
   - asserting `verify(mock).someMethod()` with no behavioral consequence
   - tests that re-state the implementation (`when(x).thenReturn(y); assertThat(service.x()).isEqualTo(y);`)
   - tests that pass for any implementation that compiles
   - one assertion per trivial getter
4. **Prefer the real stack over mocks.** This repo has Dev Services (Postgres, MinIO, Keycloak via Testcontainers). Use `@QuarkusTest` + RestAssured + the real DB/S3/OIDC unless there is a *specific* reason to mock (external network, nondeterminism, deliberate fault injection). When mocking is right, use `@InjectMock` from `quarkus-junit-mockito` — never hand-rolled mocks.
5. **Match existing conventions** (read a sibling test before writing):
   - AssertJ (`assertThat`, `assertThatThrownBy`) — never JUnit `Assertions.*` for value checks
   - Naming: `methodUnderTest_condition_expectedBehavior` (see `GameEngineTest`)
   - Package-private classes/methods; no `public` on test classes/methods
   - Static fixtures as `private static final` constants at the top, like `GameEngineTest.CARD_1`
   - Auth tests: `@TestSecurity(user = "...", roles = {"player"|"admin"})` and/or `quarkus-test-security-oidc`, mirroring `AuthorizationTest`
   - REST tests: RestAssured with `given().when().then()` chain, mirroring `GameResourceTest`
6. **Cover what actually breaks**, in priority order:
   - **State-machine illegal transitions** in `GameEngine` — every `IllegalStateException` path should have a test pinning the message or the pre/post state
   - **Concurrency invariants** — `GameEngine` mutators are `synchronized`; if you find an untested race-prone path, add a test that exercises it from multiple threads with `assertThat` on the final state
   - **WebSocket auth** — `WebSocketTokenFilter` quirks (missing token, malformed token, non-`/ws/*` path untouched)
   - **CDI event firing** — `GameRegistry` mutations must fire the correct `GameUpdateEvent` type; assert via a test observer or `@InjectMock` of the `Event<GameUpdateEvent>`
   - **Exception mappers** in `util/` — verify the HTTP status, not just that the mapper exists
   - **Authorization matrix** — for each protected endpoint, at least one allowed-role and one forbidden-role case
7. **No new test infrastructure unless asked.** Don't introduce a base test class, custom extension, or helper module on your own. If you genuinely need one, propose it first.
8. **Run the tests you write.** After adding tests, run `./mvnw test -Dtest=<NewTestClass>` and confirm green. If a test is red, decide whether the test is wrong or the production code is wrong, and tell the user — do not silently weaken the assertion.

## Workflow

1. **Survey** — list candidate gaps (untested class / untested branch / weak assertion in existing test). Cite file:line.
2. **Propose** — show the user a short numbered list with the *regression caught* for each. Wait for selection unless `$ARGUMENTS` already pinpoints one target.
3. **Implement** — add tests in the matching package under `src/test/java/`, following conventions above.
4. **Verify** — run the new tests; report pass/fail and any flakiness.
5. **Report** — one or two sentences: which gaps were closed, what was deliberately left out and why.

## Out of scope

- Don't refactor production code to make it "more testable" unless the user okays it — flag the seam instead.
- Don't add coverage tooling, mutation testing, or CI changes.
- Don't write tests for trivial Lombok-generated accessors, DTO records, or framework wiring that Quarkus already covers.
