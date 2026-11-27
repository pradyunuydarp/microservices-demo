# Lab: build, connect and break a microservice

In this lab you add a fifth service to the canteen: **review-service**, where customers
rate menu items from 1 to 5. Step by step you will:

1. build its REST API from scratch (entity, repository, service, controller);
2. test it;
3. put it in a container, in Docker Compose and behind the API gateway;
4. make it call menu-service, and make that call survive failures.

At the end there are graded exercises.

Plan for about two hours for Parts 0 to 4. Commands are written for bash (Linux, macOS,
Git Bash on Windows). In Windows cmd or PowerShell, use `mvnw.cmd` instead of `./mvnw`
and create folders and files in your IDE. Read [README.md](../README.md) first if you
have not.

---

## Part 0: Check your setup (10 min)

```bash
java -version            # should say 21
docker compose version   # should say v2 or later
./mvnw -v                # the first run downloads Maven
./mvnw verify            # runs all tests, no Docker needed
```

You should see `BUILD SUCCESS` and a reactor summary with `menu-service`,
`customer-service`, `order-service` and `api-gateway`, each `SUCCESS`.

Now start the whole system and check it:

```bash
./mvnw -q package -DskipTests
docker compose up -d --build --wait
scripts/smoke.sh
```

The last line should start with `PASS:`. Leave the system running.

Take five minutes to read `menu-service/src/main/java/com/example/canteen/menu/`. Follow
one request: `controller/MenuController` calls `service/MenuService`, which uses
`repository/MenuItemRepository` to load `domain/MenuItem` entities and returns `dto`
records. Your new service has exactly the same shape.

---

## Part 1: A REST endpoint from scratch (40 min)

### Step 1.1: Create the module

```bash
mkdir -p review-service/src/main/java/com/example/canteen/review/{controller,service,repository,domain,dto}
mkdir -p review-service/src/main/resources
mkdir -p review-service/src/test/java/com/example/canteen/review
cp customer-service/pom.xml review-service/pom.xml
```

In `review-service/pom.xml`, change `customer-service` to `review-service` in
`<artifactId>` and `<name>`, and write a new `<description>`.

In the root `pom.xml`, add the new module at the end of `<modules>`:

```xml
        <module>api-gateway</module>
        <module>review-service</module>
    </modules>
```

Maven builds every module in this list. Each module becomes one jar, and each jar is one
service you can deploy on its own.

### Step 1.2: The main class

`review-service/src/main/java/com/example/canteen/review/ReviewServiceApplication.java`:

```java
package com.example.canteen.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
```

### Step 1.3: The entity

`.../review/domain/Review.java`:

```java
package com.example.canteen.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long menuItemId; // only the id: the menu item itself lives in menu-service

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    protected Review() {
        // JPA needs a no-argument constructor.
    }

    public Review(Long menuItemId, int rating, String comment) {
        this.menuItemId = menuItemId;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }
}
```

`@Entity` means "store objects of this class in a table". Look at `menuItemId`: it is
just a number. We cannot point a foreign key at the menu table, because that table is in
another service's database. This is **Database per service**: a service owns its data,
and others reach it only through its API.

### Step 1.4: The repository

`.../review/repository/ReviewRepository.java`:

```java
package com.example.canteen.review.repository;

import com.example.canteen.review.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMenuItemIdOrderByIdAsc(Long menuItemId);
}
```

You write no SQL. Spring Data implements `save`, `findById`, `findAll` and so on, and it
builds the query for `findByMenuItemIdOrderByIdAsc` from the method name.

### Step 1.5: Request and response records

`.../review/dto/ReviewRequest.java`:

```java
package com.example.canteen.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull @Positive Long menuItemId,
        @Min(1) @Max(5) int rating,
        @Size(max = 500) String comment) {
}
```

`.../review/dto/ReviewResponse.java`:

```java
package com.example.canteen.review.dto;

import com.example.canteen.review.domain.Review;

public record ReviewResponse(Long id, Long menuItemId, int rating, String comment) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getMenuItemId(), review.getRating(), review.getComment());
    }
}
```

Why not send the entity itself as JSON? Because then every column becomes part of your
public API, and you can never change the table without breaking clients. The
annotations on `ReviewRequest` (Bean Validation) reject bad input before your code runs.

### Step 1.6: The service

`.../review/service/ReviewService.java`:

```java
package com.example.canteen.review.service;

import com.example.canteen.review.domain.Review;
import com.example.canteen.review.dto.ReviewRequest;
import com.example.canteen.review.dto.ReviewResponse;
import com.example.canteen.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository repository;

    public ReviewService(ReviewRepository repository) {
        this.repository = repository;
    }

    public ReviewResponse add(ReviewRequest request) {
        Review review = new Review(request.menuItemId(), request.rating(), request.comment());
        return ReviewResponse.from(repository.save(review));
    }

    public Optional<ReviewResponse> findById(Long id) {
        return repository.findById(id).map(ReviewResponse::from);
    }

    public List<ReviewResponse> findByMenuItem(Long menuItemId) {
        return repository.findByMenuItemIdOrderByIdAsc(menuItemId).stream().map(ReviewResponse::from).toList();
    }
}
```

### Step 1.7: The controller

`.../review/controller/ReviewController.java`:

```java
package com.example.canteen.review.controller;

import com.example.canteen.review.dto.ReviewRequest;
import com.example.canteen.review.dto.ReviewResponse;
import com.example.canteen.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> add(@Valid @RequestBody ReviewRequest request) {
        ReviewResponse created = reviewService.add(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ReviewResponse get(@PathVariable Long id) {
        return reviewService.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Review " + id + " does not exist"));
    }

    @GetMapping
    public List<ReviewResponse> forMenuItem(@RequestParam Long menuItemId) {
        return reviewService.findByMenuItem(menuItemId);
    }
}
```

`POST` answers `201 Created` with a `Location` header that says where the new review
lives. `@Valid` triggers the checks from Step 1.5; a failure becomes `400 Bad Request`.

### Step 1.8: Configuration

`review-service/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: review-service
  main:
    banner-mode: off
  datasource:
    url: jdbc:h2:mem:review
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  mvc:
    problemdetails:
      enabled: true # Spring's own errors (400, 404, ...) as RFC 9457 problem details

server:
  port: 8084
  request:
    header:
      forwarded:
        strategy: framework
        naming: x-forwarded
        use-prefix: true

management:
  endpoints:
    web:
      exposure:
        include: health

logging:
  level:
    root: warn
    com.example.canteen: info
```

- Port 8084, so it does not clash with the other services.
- With no other settings the service uses an in-memory H2 database. Compose will switch
  it to PostgreSQL with environment variables, without changing this file.
- `problemdetails.enabled` makes Spring's own errors (400, 404, ...) come out as RFC 9457
  problem details. The other services go further with their own `ApiExceptionHandler`.
- `forwarded` makes `Location` headers correct behind the gateway (Part 3).

### Step 1.9: Run it and call it

```bash
./mvnw spring-boot:run -pl review-service
```

Wait for `Started ReviewServiceApplication`. In a second terminal:

```bash
curl -i -X POST http://localhost:8084/reviews \
  -H "Content-Type: application/json" \
  -d '{"menuItemId": 1, "rating": 5, "comment": "Crispy!"}'
```

You should see:

```
HTTP/1.1 201
Location: http://localhost:8084/reviews/1
Content-Type: application/json

{"id":1,"menuItemId":1,"rating":5,"comment":"Crispy!"}
```

Then try:

```bash
curl "http://localhost:8084/reviews?menuItemId=1"   # [{"id":1,"menuItemId":1,"rating":5,"comment":"Crispy!"}]
curl http://localhost:8084/reviews/9                # {"detail":"Review 9 does not exist","status":404,"title":"Not Found"}
curl -X POST http://localhost:8084/reviews \
  -H "Content-Type: application/json" \
  -d '{"menuItemId": 1, "rating": 6}'               # {"detail":"Invalid request content.","status":400,"title":"Bad Request"}
```

Stop the service with Ctrl+C. Start it again and list the reviews: they are gone,
because H2 keeps everything in memory. In Part 3 the data moves to PostgreSQL.

---

## Part 2: Test it (15 min)

`review-service/src/test/java/com/example/canteen/review/ReviewControllerTest.java`:

```java
package com.example.canteen.review;

import com.example.canteen.review.controller.ReviewController;
import com.example.canteen.review.dto.ReviewResponse;
import com.example.canteen.review.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReviewService reviewService;

    @Test
    void addReturns201WithLocation() throws Exception {
        given(reviewService.add(any())).willReturn(new ReviewResponse(1L, 1L, 5, "Crispy!"));

        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content("""
                        {"menuItemId": 1, "rating": 5, "comment": "Crispy!"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/reviews/1"));
    }

    @Test
    void ratingAboveFiveIs400() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content("""
                        {"menuItemId": 1, "rating": 6}"""))
                .andExpect(status().isBadRequest());
    }
}
```

`@WebMvcTest` starts only the web layer: your controller, JSON and validation. No
database, no server. `@MockitoBean` puts a fake `ReviewService` in its place, so the test
checks only the controller. Run it:

```bash
./mvnw -pl review-service test
```

You should see `Tests run: 2, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

Now prove the test works: delete `@Max(5)` from `ReviewRequest`, run the test again and
watch `ratingAboveFiveIs400` fail. Put `@Max(5)` back.

---

## Part 3: Containerize it and put it behind the gateway (25 min)

### Step 3.1: Dockerfile

`review-service/Dockerfile`:

```dockerfile
# Build the jar first on your machine:  ./mvnw -q package -DskipTests
# This image only copies that jar in, so it builds in seconds and needs no Maven.
FROM eclipse-temurin:21-jre-alpine

# Do not run as root inside the container.
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

COPY target/review-service.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

The image only copies the jar that Maven built. Nothing is compiled inside Docker.

### Step 3.2: Add it to Docker Compose

In `docker-compose.yml`, add these two services next to the others (indent them like
the others, under `services:`):

```yaml
  review-service:
    build: ./review-service
    ports:
      - "8084:8084" # debug only - real clients go through the gateway
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://review-db:5432/review
      SPRING_DATASOURCE_USERNAME: review
      SPRING_DATASOURCE_PASSWORD: review
    depends_on:
      review-db:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8084/actuator/health"]
      interval: 5s
      timeout: 3s
      retries: 30

  review-db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: review
      POSTGRES_USER: review
      POSTGRES_PASSWORD: review
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U review -d review"]
      interval: 3s
      timeout: 3s
      retries: 20
```

Also tell the gateway where the new service is. Under `api-gateway:` → `environment:` add:

```yaml
      REVIEW_SERVICE_URL: http://review-service:8084
```

`review-service` is both the Compose service name and the host name: Docker's DNS
resolves it. That is the whole service discovery here.

### Step 3.3: Add a gateway route

In `api-gateway/src/main/resources/application.yml`, add a fourth route after the
`order-service` route:

```yaml
            - id: review-service
              uri: ${REVIEW_SERVICE_URL:http://localhost:8084}
              predicates:
                - Path=/api/reviews/**
              filters:
                - StripPrefix=1
```

### Step 3.4: Build, start, call

```bash
./mvnw -q package -DskipTests
docker compose up -d --build --wait
docker compose ps
```

`review-service` and `review-db` should be `healthy`. Now go through the gateway:

```bash
curl -i -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"menuItemId": 1, "rating": 5, "comment": "Crispy!"}'
```

You should see `HTTP/1.1 201 Created` and
`Location: http://localhost:8080/api/reviews/1`: the address of the gateway, not of the
container. Check that the gateway knows the route:
`curl http://localhost:8080/actuator/gateway/routes`.

Questions to answer in your notes:

1. Why did you have to rebuild the **api-gateway** jar too?
2. Why does `review-db` have no `ports:` entry?
3. Restart only the service: `docker compose restart review-service`. Are the reviews still
   there? Why, when in Step 1.9 they were not?

---

## Part 4: Call another service, and handle failure (40 min)

### Step 4.1: The problem

```bash
curl -i -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" \
  -d '{"menuItemId": 999, "rating": 1}'
```

`201 Created`: a review for a dish that does not exist. review-service must ask
menu-service first.

### Step 4.2: A REST client

Add the HTTP client starter to `review-service/pom.xml`, inside `<dependencies>`:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-restclient</artifactId>
        </dependency>
```

Create `.../review/client/MenuClient.java`:

```java
package com.example.canteen.review.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class MenuClient {

    private final RestClient restClient;

    public MenuClient(RestClient.Builder builder, @Value("${menu-service.url}") String menuServiceUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(1));
        timeouts.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = builder.baseUrl(menuServiceUrl).requestFactory(timeouts).build();
    }

    /** True if menu-service knows this item. Throws if menu-service cannot be reached. */
    public boolean exists(Long menuItemId) {
        try {
            restClient.get().uri("/menu/{id}", menuItemId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound notFound) {
            return false; // a normal answer, not a failure
        }
    }
}
```

Two things matter here:

- **Timeouts.** Never call another service without them. If menu-service hangs, a call
  without a read timeout waits forever, and each waiting request holds one of your threads.
- **404 is an answer, not a failure.** "No such item" is normal business. A network error
  is not. We return `false` for the first and let the second throw.

### Step 4.3: Use it in the service

In `ReviewService`, take a `MenuClient` in the constructor next to the repository, and
replace `add` with:

```java
    public ReviewResponse add(ReviewRequest request) {
        if (!menuClient.exists(request.menuItemId())) {
            // Shortcut: an HTTP status thrown from the service layer. The other services
            // use their own exception classes plus an ApiExceptionHandler instead.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Menu item " + request.menuItemId() + " does not exist");
        }
        Review review = new Review(request.menuItemId(), request.rating(), request.comment());
        return ReviewResponse.from(repository.save(review));
    }
```

(Add the imports for `MenuClient`, `HttpStatus` and `ResponseStatusException`; your IDE
offers them.)

### Step 4.4: Configure the URL

In `review-service/src/main/resources/application.yml`, add at the top level:

```yaml
menu-service:
  url: ${MENU_SERVICE_URL:http://localhost:8081}
```

Locally that is the menu-service debug port. In `docker-compose.yml`, under
`review-service:` → `environment:`, add:

```yaml
      MENU_SERVICE_URL: http://menu-service:8081
```

### Step 4.5: Try it

```bash
./mvnw -q package -DskipTests
docker compose up -d --build --wait
curl -X POST http://localhost:8080/api/reviews -H "Content-Type: application/json" -d '{"menuItemId": 99, "rating": 4}'
curl -X POST http://localhost:8080/api/reviews -H "Content-Type: application/json" -d '{"menuItemId": 1, "rating": 4}'
```

The first gives `{"detail":"Menu item 99 does not exist","status":422,...}`, the second
`201`. Your test from Part 2 still passes (`./mvnw -pl review-service test`): it mocks
`ReviewService`, so it never touches `MenuClient`.

### Step 4.6: Break it

```bash
docker compose stop menu-service
curl -s -w ' -> %{http_code} in %{time_total}s\n' -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" -d '{"menuItemId": 1, "rating": 4}'
```

You get `500 Internal Server Error` after about 1 second (the connect timeout). Two
problems:

- **500 is the wrong message.** It says "review-service has a bug". The truth is "a
  service I need is down, try later", which is `503 Service Unavailable`.
- **Every request still waits for the timeout** and still hits a service we already know
  is down.

Optional: `docker compose start menu-service`, wait 20 seconds, then
`docker compose pause menu-service` and send the request again. It fails after 2 seconds:
that is the read timeout. Now comment out the `setReadTimeout` line, rebuild, and try
again. How long does it wait now, who finally stops the wait, and with which status code?
(Look at `httpclient:` in the gateway's `application.yml`.) Put the line back and run
`docker compose unpause menu-service`.

### Step 4.7: Add a circuit breaker

Add Resilience4j to `review-service/pom.xml`. The `@CircuitBreaker` annotation works
through AOP, so it needs the AspectJ starter too:

```xml
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot4</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aspectj</artifactId>
        </dependency>
```

Annotate `exists` and add a fallback method to `MenuClient`. The whole file now reads:

```java
package com.example.canteen.review.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Component
public class MenuClient {

    private final RestClient restClient;

    public MenuClient(RestClient.Builder builder, @Value("${menu-service.url}") String menuServiceUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(1));
        timeouts.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = builder.baseUrl(menuServiceUrl).requestFactory(timeouts).build();
    }

    /** True if menu-service knows this item. Throws if menu-service cannot be reached. */
    @CircuitBreaker(name = "menu-service", fallbackMethod = "menuUnavailable")
    public boolean exists(Long menuItemId) {
        try {
            restClient.get().uri("/menu/{id}", menuItemId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound notFound) {
            return false; // a normal answer, not a failure
        }
    }

    // Called instead when exists() throws, or when the circuit is OPEN.
    private boolean menuUnavailable(Long menuItemId, Throwable cause) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "menu-service is unavailable", cause);
    }
}
```

Configure it in `application.yml`, and expose the `circuitbreakers` actuator endpoint
(change `include: health` to `include: health,circuitbreakers`):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      menu-service:
        sliding-window-size: 5
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

In words: look at the last 5 calls; if at least half failed, **open** the circuit. While
open, do not call menu-service at all; fail at once. After 10 seconds go **half-open**
and let a few trial calls through. If they work, **close** again.

### Step 4.8: Break it again

```bash
./mvnw -q package -DskipTests
docker compose up -d --build --wait
docker compose stop menu-service
for i in 1 2 3 4 5 6; do
  curl -s -w ' -> %{http_code} in %{time_total}s\n' -X POST http://localhost:8080/api/reviews \
    -H "Content-Type: application/json" -d '{"menuItemId": 1, "rating": 4}'
done
curl http://localhost:8084/actuator/circuitbreakers
```

Every answer is now `{"detail":"menu-service is unavailable","status":503,...}`, and the
breaker's `"state"` is `"OPEN"`. Bring menu-service back and watch it recover:

```bash
docker compose start menu-service
```

For a while you still get 503: menu-service needs some seconds to start, and the breaker
waits 10 seconds before it tries again. Then the breaker goes `HALF_OPEN`, a trial call
works, and reviews are accepted again. It stays `HALF_OPEN` until 10 trial calls (the
Resilience4j default) have worked, then it is `CLOSED`. Send a few more reviews and check.

### Step 4.9: What you built

| You did | Pattern | In the book |
|---|---|---|
| review-service with its own review-db | Database per service | ch. 1 |
| `/api/reviews/**` route in the gateway | API Gateway | ch. 8 |
| `http://menu-service:8081` resolved by Docker | Service discovery provided by the platform | ch. 3 |
| `MenuClient` with `RestClient` | Remote procedure invocation (REST) | ch. 3 |
| timeouts + `@CircuitBreaker` + 503 fallback | Circuit breaker | ch. 3 |
| `/actuator/health` + compose `healthcheck` | Health check API | ch. 11 |

To clean up at the end of the lab: `docker compose down -v`.

---

## Part 5: Exercises (graded)

Hand in your code (a git branch or a zip) and a short `ANSWERS.md`. Each exercise is
graded on three things: it works (paste curl output or a passing test), it has a test,
and the written answers are clear. Total: 100 points.

### Exercise 1: `PATCH /menu/{id}/availability` (15 points)

The canteen sells out of things during the day. Add an endpoint to **menu-service**:

```
PATCH /menu/3/availability
Content-Type: application/json

{"available": false}
```

- Answer `200` with the updated item; `404` (problem detail) for an unknown id; `400` if
  `available` is missing.
- Add a `@WebMvcTest` test for the happy path and for the 400, and add the request to
  `requests.http`.
- Then mark Filter Coffee as unavailable and try to order it through the gateway.

Answer: Why does order-service see the change at once? An order placed *before* the
change still shows Filter Coffee with its price. Why is that correct?

### Exercise 2: API composition, `GET /api/orders/{id}/details` (25 points)

Clients want an order together with the customer's name. Add
`GET /orders/{id}/details` to **order-service**. It loads the order from its own
database, calls customer-service through `CustomerClient`, and returns both:

```json
{"order": {"id": 1, "customerId": 1, "lines": [...], "total": 140.00},
 "customer": {"id": 1, "name": "Asha"}}
```

- Test the new `OrderService` method with mocked clients, like `OrderServiceTest` does.
- Decide what happens when customer-service is down: answer `503`, or return the order
  with `"customer": null` (a partial result)? Implement one, test it, and justify it.

Answer: This is the book's API composition pattern (ch. 7). Here order-service is the
composer. The gateway could be the composer instead (ch. 8). Give one advantage of each
choice.

### Exercise 3: Retries with backoff (25 points)

Add a Resilience4j retry to `MenuClient.findItems` in order-service:
`@Retry(name = "menu-service")` next to `@CircuitBreaker`, and in `application.yml`:

```yaml
resilience4j:
  retry:
    instances:
      menu-service:
        max-attempts: 3
        wait-duration: 200ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
```

(Merge this with the existing `resilience4j:` block.) Then stop menu-service and time
an order with `curl -w '%{time_total}'`, before and after the circuit opens.

Answer in 5 to 10 sentences:

1. How long does a failing order take now, and why? Show the arithmetic.
2. Resilience4j runs Retry *outside* CircuitBreaker by default. What happens to a call
   when the circuit is open? Is retrying that useful?
3. Why do retries need timeouts? Why do they need a circuit breaker? (Think of 100
   clients all retrying against a service that is already overloaded.)
4. Why is it dangerous to retry `POST /orders` blindly, but safe to retry
   `GET /menu?ids=...`?

### Exercise 4: Scale order-service to 2 instances (20 points)

1. In `docker-compose.yml`, comment out the `ports:` lines of `order-service`.
   Explain why this is necessary before scaling.
2. Run `docker compose up -d --scale order-service=2 --wait` and check with
   `docker compose ps order-service`.
3. Place 10 orders through the gateway, then run
   `docker compose logs order-service | grep "Placed order"`. Each line starts with the
   name of the container that handled it.

Answer:

1. Were the orders spread evenly? Explain what you saw. (Hint: Docker's DNS returns both
   addresses; the gateway caches DNS answers for 5 seconds and reuses open connections.)
2. Both instances use the same `order-db`. Why is that fine, while letting menu-service
   read `order-db` would not be?
3. Each instance has its own circuit breakers. What does that mean when menu-service goes
   down?
4. Stop one instance with `docker stop <container name>` and keep ordering. What happens?
   What would Kubernetes do differently (ch. 12)?

### Exercise 5: A gateway that answers 503 (15 points)

When menu-service is down, `GET /api/menu` gives a plain `500` from the gateway.
Make it answer `503` with a problem detail instead.

Hints: Spring Cloud Gateway has a `CircuitBreaker` route filter with a `fallbackUri`
(add `spring-cloud-starter-circuitbreaker-reactor-resilience4j` to the gateway), and the
fallback can be a small `@RestController` inside the gateway. Extend
`ApiGatewayRoutesTest` to cover it.

Answer: Now there is a circuit breaker in the gateway *and* in order-service. Is that
redundant? What does each one protect?
