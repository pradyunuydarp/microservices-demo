# Guide for AI coding agents (and humans)

A teaching boilerplate: a campus canteen as five Spring Boot REST services plus Docker
Compose. Students read this code, so clarity beats cleverness. Keep it small. The docs
follow the Microservices lecture (defining, securing, testing, deploying); `docs/SLIDES.md`
maps each slide to the repo.

## Layout

- `pom.xml`: parent build. Java 21, Spring Boot 4.2.0 (Spring Security 7.2), Spring Cloud
  2026.0.0, Spring Cloud Contract 5.0.3 (own BOM: it is not in the release train),
  Resilience4j 2.4.0, springdoc 3.2.1. Modules: `menu-service` (8081), `customer-service`
  (8082), `order-service` (8083), `auth-service` (8090), `api-gateway` (8080).
- Each business service: `src/main/java/com/example/canteen/<name>/{controller,service,repository,domain,dto}`,
  `src/main/resources/application.yml`, `Dockerfile`, tests in `src/test/java`.
- `order-service/.../client/`: `RestClient` beans with timeouts, `MenuClient` and
  `CustomerClient` with `@CircuitBreaker` fallbacks that throw `ServiceUnavailableException` (503).
  They send `Accept: application/json`; the contracts require it.
- `auth-service`: OAuth 2.0 authorization server (Spring Security's, via
  `spring-boot-starter-security-oauth2-authorization-server`). Clients, dev-only secrets,
  scopes and the fixed issuer `http://auth-service:8090` are in `application.yml`;
  `SecurityConfig` turns on the endpoints and opens only `/actuator/health`. Client
  credentials grant; clients must ask for scopes explicitly.
- `api-gateway`: routes in `application.yml` (`spring.cloud.gateway.server.webflux.routes`),
  including `POST /oauth2/token` to auth-service. It is an OAuth2 resource server:
  `SecurityConfig` (WebFlux) holds the rules (`GET /api/menu/**` public, writes need
  `SCOPE_menu.write`, `/api/orders/**` `SCOPE_orders`, `/api/customers/**`
  `SCOPE_customers`, other `/api/**` authenticated); JWKS URL and issuer in `application.yml`.
  Services behind it do not check tokens (perimeter security, documented in docs/SECURING.md).
- Contracts (Spring Cloud Contract, YAML): `menu-service` and `customer-service` have
  `src/test/resources/contracts/order-service/*.yml`, a `contracts/ContractVerifierBase`
  test class, and the `spring-cloud-contract-maven-plugin` (incremental mode off). They
  publish `target/<service>-stubs.jar`; order-service depends on both stubs jars (test
  scope, classifier `stubs`) and `ConsumerContractTest` runs them with Stub Runner on
  ports 18081/18082.
- `customer-service`: `CustomerRepositoryPostgresTest` uses Testcontainers 2
  (`@Testcontainers(disabledWithoutDocker = true)`, `org.testcontainers.postgresql.PostgreSQLContainer`).
- `docker-compose.yml`: the 5 services and `menu-db`, `customer-db`, `order-db` (PostgreSQL 17).
  Java services are hardened (`no-new-privileges`, `cap_drop: [ALL]`, `read_only`, tmpfs `/tmp`).
- `scripts/smoke.sh`: end-to-end test through the gateway: tokens, 401/403, orders, the
  failure demo. Setup/Execute/Verify/Teardown phases are labelled.
- `.github/workflows/ci.yml`: `test` (`./mvnw -B verify`), `smoke` (Compose), `publish`
  (push to `main` only: images to `ghcr.io/<owner>/<repo>/<service>:<sha>` and `:latest`).
- `docs/`: `SLIDES.md`, `DEFINING.md`, `SECURING.md`, `TESTING.md`, `DEPLOYING.md`, `LAB.md`.
  LAB's code blocks were tested; keep them in sync with the code.

## Commands

```bash
./mvnw verify                          # all tests (run before every commit); NOT ./mvnw test
./mvnw verify -pl order-service -am    # one service plus the modules it needs (the stubs)
./mvnw -q package -DskipTests          # build jars (Dockerfiles copy target/<module>.jar)
docker compose up -d --build --wait    # start everything
scripts/smoke.sh                       # must end with PASS
docker compose down -v                 # stop and delete data
./mvnw spring-boot:run -pl menu-service   # one service on H2, no Docker
```

`./mvnw test` cannot run `ConsumerContractTest`: the stubs jars are built in `package`.

## Rules

- **Boot 4, not Boot 3.** Starters are `spring-boot-starter-webmvc`, `-restclient`,
  `-aspectj`, `-security-oauth2-authorization-server`, `-security-oauth2-resource-server`;
  tests use `spring-boot-starter-webmvc-test`, `-webflux-test`, `-data-jpa-test`,
  `-security-oauth2-*-test`; `@WebMvcTest` is in `org.springframework.boot.webmvc.test.autoconfigure`,
  `@AutoConfigureWebTestClient` in `org.springframework.boot.webtestclient.autoconfigure`,
  `@DataJpaTest` in `org.springframework.boot.data.jpa.test.autoconfigure`; use `@MockitoBean`;
  Jackson is 3 (`tools.jackson`). Check that an artifact or import really resolves before using it.
- Services do not share code or databases. No common module; small duplication is intended.
  A service reaches another only through its REST API. A stubs jar is test data, not code.
- Changing a provider's API? Change its contract YAML first and make both sides pass.
- Layering: controller (HTTP only) → service (logic) → repository. Entities never leave
  the service layer; the API uses DTO records.
- Errors are RFC 9457 `ProblemDetail`s from the service's one `@RestControllerAdvice`.
- Every outbound HTTP call has connect and read timeouts and a circuit breaker. A 404
  from a downstream is a normal answer (return empty), not a failure.
- Never hold a database transaction open while calling another service.
- Config comes from `application.yml` with env-var overrides and localhost defaults;
  defaults must work with H2 and no Docker.
- No real secrets in the repo. Compose passwords and the OAuth client secrets are obvious
  dev-only values (`menu`/`menu`, `canteen-app-secret`) and documented as such.
- Tests: `@WebMvcTest` for controllers, plain Mockito for service logic, real HTTP to a
  JDK `HttpServer` fake for clients, `mockJwt()` for gateway rules. Parameterize where it
  reads naturally. Keep them short.
- Write docs in simple words and short sentences. No marketing language. Refer to "the
  Microservices lecture" and slide numbers; do not copy slide text.
- Before saying something works: `./mvnw verify`, and for anything touching a flow,
  `scripts/smoke.sh` against a running stack. Report the real output.
- Commits: imperative subject, one concern per commit.
