# Slide map: the Microservices lecture in this repo

The lecture deck has 30 slides in four parts: defining, securing, testing and deploying
microservices. This table walks the deck in order. For each slide it says what the slide
means in this repo and what to show in class.

Before class: `./mvnw -q package -DskipTests && docker compose up -d --build --wait`.
Most commands below assume the system is running and, from slide 15 on, that `$TOKEN`
holds a canteen-app token:

```bash
TOKEN=$(curl -s -u canteen-app:canteen-app-secret -d grant_type=client_credentials \
  -d "scope=orders customers" http://localhost:8080/oauth2/token \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

| # | Slide | In this repo | Show in class |
|---|---|---|---|
| 1 | Microservices (title) | A campus canteen as five small services, modelled on the FTGO example of *Microservices Patterns*. | [README.md](../README.md), the architecture diagram |
| 2 | Outline | The README and the `docs/` pages follow the same four parts. | README sections 1 to 4 |
| 3 | MSA vs SOA | Each service offers one business capability over REST: a service-oriented architecture built from microservices. Other ways: SOAP web services with an ESB, remote objects. | [DEFINING.md](DEFINING.md#microservices-and-soa-slides-3-and-30), `docker compose ps` |
| 4 | Key concepts | Service orientation: services call each other only over HTTP. Independently deployable: one module, jar, image and database each. Bounded context: menu item as an entity in menu-service, as a price snapshot in order-service. Own stack: reactive gateway, servlet services. | `MenuItem.java` next to `OrderLine.java`; `docker compose up -d --build menu-service` |
| 5 | Microservices and DevOps | Each service is a small deployment unit; CI builds, tests and publishes each one. | [.github/workflows/ci.yml](../.github/workflows/ci.yml), the Actions tab on GitHub |
| 6 | Enabling tools and technologies | Containers: Dockerfiles and Compose. API gateway: `api-gateway`. Messaging: in [eda-demo](https://github.com/pradyunuydarp/eda-demo). | `docker-compose.yml`, `curl http://localhost:8080/actuator/gateway/routes` |
| 7 | 1. Defining microservices | Section start. | [DEFINING.md](DEFINING.md) |
| 8 | Characteristics of "micro" | Size: 60 to 600 lines of Java per service. Implications: 8 containers, so health checks, start order, timeouts and circuit breakers; more to administer. | `docker compose ps`; "Break it" in the README; smoke steps 8 to 10 |
| 9 | Why "micro"? | Loose coupling (own DTOs, tolerant reader), CI/CD, one module per team, mixed platforms. | [DEFINING.md](DEFINING.md#why-micro-slide-9), `CustomerDto.java` (2 fields) vs `CustomerResponse.java` (3 fields) |
| 10 | Important tasks | Functional decomposition: menu, customers, orders. One service per module: the root `pom.xml`. Common services: gateway and auth-service. Dependencies: diagram, `depends_on`, timeouts, breakers, contracts. Lifecycle: develop, test, deploy in CI. | [DEFINING.md](DEFINING.md#important-tasks-slide-10), `<modules>` in [pom.xml](../pom.xml) |
| 11 | Domain modelling (for DB design) | One database per service (menu-db, customer-db, order-db). How to switch to per family or per application, and why not to. | `docker-compose.yml`; `Order.customerId` is a plain number; [DEFINING.md](DEFINING.md#domain-modelling-for-db-design-slide-11) |
| 12 | 2. Securing microservices | Section start. | [SECURING.md](SECURING.md) |
| 13 | Several layers of security | Service: OAuth 2.0 tokens and scopes. Containers: non-root, read-only, no capabilities. Infrastructure: one entry point, no database ports. | The three-layer table in the README |
| 14 | Securing services using OAuth | auth-service is the one security service for everything. The gateway checks its tokens for every service behind it. | [auth-service's application.yml](../auth-service/src/main/resources/application.yml) (clients, scopes); [api-gateway's SecurityConfig](../api-gateway/src/main/java/com/example/canteen/gateway/SecurityConfig.java) (rules) |
| 15 | Typical flow | 1. call without token: 401. 2. the gateway stops it; auth-service checks the client. 3. `POST /oauth2/token` returns a signed JWT. 4. the call with `Authorization: Bearer`: 201. Wrong scope: 403. | Run the four steps in [SECURING.md](SECURING.md#the-typical-flow-step-by-step); decode the token's middle part |
| 16 | Securing the containers | Small JRE-only images, `USER app`, and in Compose `no-new-privileges`, `cap_drop: [ALL]`, `read_only`. Scanning images is the next step. | `docker compose exec menu-service touch /app/x` (Read-only file system); `docker scout cves microservices-demo-menu-service` if installed |
| 17 | Securing the infrastructure | Gateway as the one reverse proxy; databases not published. Not here: TLS, a firewall. The debug ports are a hole on purpose. | `curl -X POST http://localhost:8083/orders ...` works without a token: why that is bad |
| 18 | 3. Testing microservices | Section start. | [TESTING.md](TESTING.md) |
| 19 | Essentials for microservices testing | Automation: `./mvnw verify` (75 tests) and CI. Stubs and drivers. Unit, component, integration, contract, end-to-end tests. | `./mvnw verify`; the test table in the README |
| 20 | Automated testing lifecycle | Setup, Execute, Verify, Teardown, labelled in the code. | `# Setup` ... `# Teardown` comments in [scripts/smoke.sh](../scripts/smoke.sh); [CustomerRepositoryPostgresTest](../customer-service/src/test/java/com/example/canteen/customer/CustomerRepositoryPostgresTest.java) |
| 21 | Stubs and drivers | Stubs: the JDK fake server in `DownstreamCallsTest`, WireMock stubs from the contracts, `FakeBackend` behind the gateway. Drivers: `scripts/smoke.sh`, MockMvc, `requests.http`. | Run `scripts/smoke.sh`; open `DownstreamCallsTest` |
| 22 | Contract testing | Spring Cloud Contract YAML contracts: method and path, the `Accept` header, the response status, headers and fields. Provider tests and consumer stubs from the same file. | [findItemsByIds.yml](../menu-service/src/test/resources/contracts/order-service/findItemsByIds.yml); the generated test in `menu-service/target/generated-test-sources/contracts`; LAB Part 6 (break a contract) |
| 23 | 4. Deployment | Section start. | [DEPLOYING.md](DEPLOYING.md) |
| 24 | Monolith deployment | Every service is a runnable jar; on a server you would add a JDK and systemd. Caveat: service tied to machine. | `java -jar menu-service/target/menu-service.jar` |
| 25 | Dynamic virtual machines | Not used here; one VM image per service, more isolation, much bigger and slower. | [DEPLOYING.md](DEPLOYING.md#2-virtual-machine-images-slide-25) |
| 26 | Container deployment | Package: `docker build`. Registry: CI pushes every image to ghcr.io. Run: `docker run` or Compose. Container bloat: 8 containers already, so Kubernetes next. | `docker build`, `tag`, `push`, `run` in [DEPLOYING.md](DEPLOYING.md#3-containers-slide-26); the `publish` job in CI; the Packages tab on GitHub |
| 27 | Serverless deployment | Not used here; what would change for a function. | [DEPLOYING.md](DEPLOYING.md#4-serverless-slide-27) |
| 28 | Summary | The topics of the deck, which the README follows in order. | README table of contents |
| 29 | Enterprise software architecture | No single style fits everything: this repo is synchronous REST; the companion repo is event-driven. Decide the architecture first, on purpose. | Compare with [eda-demo](https://github.com/pradyunuydarp/eda-demo) |
| 30 | SOA with microservices | SOA realized with REST microservices, as here, or with SOAP web services. | The service table in [DEFINING.md](DEFINING.md#microservices-and-soa-slides-3-and-30) |
