package com.example.canteen.order;

import com.example.canteen.order.client.CustomerClient;
import com.example.canteen.order.client.MenuClient;
import com.example.canteen.order.client.ServiceUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real order-service (RestClient, timeouts, circuit breakers, H2) talking over real HTTP
 * to a tiny fake menu-service and customer-service built on the JDK's own HTTP server.
 * The fake can answer normally, fail with an error status, or be slow.
 */
@SpringBootTest(properties = "downstream.read-timeout=500ms") // shorter than the real 2s, keeps the test fast
@AutoConfigureMockMvc
class DownstreamCallsTest {

    static final HttpServer fake = startFakeServices();
    static final AtomicInteger menuCalls = new AtomicInteger();
    static volatile int menuStatus;
    static volatile Duration menuDelay;
    static volatile String menuQuery;

    @DynamicPropertySource
    static void pointClientsAtFake(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + fake.getAddress().getPort();
        registry.add("downstream.menu-service-url", () -> url);
        registry.add("downstream.customer-service-url", () -> url);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    MenuClient menuClient;
    @Autowired
    CustomerClient customerClient;
    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void reset() {
        menuStatus = 200;
        menuDelay = Duration.ZERO;
        menuCalls.set(0);
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopFake() {
        fake.stop(0);
    }

    @Test
    void placesOrderUsingBothServices() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 1, "items": [{"menuItemId": 1, "quantity": 2}, {"menuItemId": 3, "quantity": 1}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].name").value("Masala Dosa"))
                .andExpect(jsonPath("$.total").value(140.00));
        assertThat(menuQuery).isEqualTo("ids=1,3"); // one batch call
    }

    @Test
    void unknownCustomerIsNotAFailure() {
        assertThat(customerClient.findCustomer(42L)).isEmpty();
        assertThat(circuitBreakers.circuitBreaker("customer-service").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void serverErrorBecomes503() throws Exception {
        menuStatus = 500;

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 1, "items": [{"menuItemId": 1, "quantity": 1}]}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.service").value("menu-service"));
    }

    @Test
    void slowServiceTimesOutQuickly() {
        menuDelay = Duration.ofSeconds(3);
        long start = System.nanoTime();

        assertThatThrownBy(() -> menuClient.findItems(List.of(1L)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasCauseInstanceOf(ResourceAccessException.class); // the read timed out
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void circuitOpensAfterRepeatedFailuresThenFailsFast() {
        menuStatus = 500;
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> menuClient.findItems(List.of(1L))).isInstanceOf(ServiceUnavailableException.class);
        }
        assertThat(circuitBreakers.circuitBreaker("menu-service").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> menuClient.findItems(List.of(1L)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("circuit breaker is open");
        assertThat(menuCalls).hasValue(5); // the 6th call never left order-service
    }

    // ---- the fake services ----

    private static HttpServer startFakeServices() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/customers/", exchange -> {
                boolean known = exchange.getRequestURI().getPath().equals("/customers/1");
                respond(exchange, known ? 200 : 404, known ? """
                        {"id": 1, "name": "Asha", "email": "asha@campus.edu"}""" : "{}");
            });
            server.createContext("/menu", exchange -> {
                menuCalls.incrementAndGet();
                menuQuery = exchange.getRequestURI().getQuery();
                sleep(menuDelay);
                respond(exchange, menuStatus, menuStatus != 200 ? "{}" : """
                        [{"id": 1, "name": "Masala Dosa", "price": 60.00, "available": true},
                         {"id": 3, "name": "Filter Coffee", "price": 20.00, "available": true}]""");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
