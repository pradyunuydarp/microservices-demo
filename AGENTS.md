# Guide for AI coding agents (and humans)

A teaching boilerplate: a campus canteen as four Spring Boot REST services plus Docker
Compose. Students read this code, so clarity beats cleverness. Keep it small.

## Layout

- `pom.xml`: parent build. Java 21, Spring Boot 4.2.0, Spring Cloud 2026.0.0,
  Resilience4j 2.4.0, springdoc 3.2.1. Modules: `menu-service` (8081),
  `customer-service` (8082), `order-service` (8083), `api-gateway` (8080).
- Each service: `src/main/java/com/example/canteen/<name>/{controller,service,repository,domain,dto}`,
  `src/main/resources/application.yml`, `Dockerfile`, tests in `src/test/java`.
- `order-service/.../client/`: `RestClient` beans with timeouts, `MenuClient` and
  `CustomerClient` with `@CircuitBreaker` fallbacks that throw `ServiceUnavailableException` (503).
- `api-gateway`: routes in `application.yml` (`spring.cloud.gateway.server.webflux.routes`).
- `docker-compose.yml`: the 4 services and `menu-db`, `customer-db`, `order-db` (PostgreSQL 17).
- `scripts/smoke.sh`: end-to-end test through the gateway, including the failure demo.
- `docs/LAB.md`: the class lab. Its code blocks were tested; keep them in sync with the code.

## Commands

```bash
./mvnw verify                          # all tests, no Docker (run before every commit)
./mvnw -q package -DskipTests          # build jars (Dockerfiles copy target/<module>.jar)
docker compose up -d --build --wait    # start everything
scripts/smoke.sh                       # must end with PASS
docker compose down -v                 # stop and delete data
./mvnw spring-boot:run -pl menu-service   # one service on H2, no Docker
```

## Rules

- **Boot 4, not Boot 3.** Starters are `spring-boot-starter-webmvc`, `-restclient`,
  `-aspectj`; tests use `spring-boot-starter-webmvc-test`; `@WebMvcTest` is in
  `org.springframework.boot.webmvc.test.autoconfigure`; use `@MockitoBean`; Jackson is 3
  (`tools.jackson`). Check that an artifact or import really resolves before using it.
- Services do not share code or databases. No common module; small duplication is intended.
  A service reaches another only through its REST API.
- Layering: controller (HTTP only) → service (logic) → repository. Entities never leave
  the service layer; the API uses DTO records.
- Errors are RFC 9457 `ProblemDetail`s from the service's one `@RestControllerAdvice`.
- Every outbound HTTP call has connect and read timeouts and a circuit breaker. A 404
  from a downstream is a normal answer (return empty), not a failure.
- Never hold a database transaction open while calling another service.
- Config comes from `application.yml` with env-var overrides and localhost defaults;
  defaults must work with H2 and no Docker.
- No secrets in the repo. Compose passwords are obvious local values (`menu`/`menu`).
- Tests: `@WebMvcTest` for controllers, plain Mockito for service logic, real HTTP to a
  JDK `HttpServer` fake for clients. Parameterize where it reads naturally. Keep them short.
- Write docs in simple words and short sentences. No marketing language.
- Before saying something works: `./mvnw verify`, and for anything touching a flow,
  `scripts/smoke.sh` against a running stack. Report the real output.
- Commits: imperative subject, one concern per commit.
