package com.example.canteen.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Starts the real gateway and one fake backend that answers with the path it received,
 * then checks that each /api/... route reaches the right place with /api stripped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutesTest {

    static final HttpServer backend = startEchoBackend();

    @DynamicPropertySource
    static void routeToBackend(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + backend.getAddress().getPort();
        registry.add("MENU_SERVICE_URL", () -> url);
        registry.add("CUSTOMER_SERVICE_URL", () -> url);
        registry.add("ORDER_SERVICE_URL", () -> url);
    }

    @LocalServerPort
    int port;

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            /api/menu           | /menu
            /api/menu?ids=1,2   | /menu?ids=1,2
            /api/customers/7    | /customers/7
            /api/orders         | /orders
            """)
    void routesAndStripsPrefix(String gatewayPath, String backendPath) {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        client.get().uri(gatewayPath).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Seen-Forwarded-Prefix", "/api")
                .expectBody(String.class).isEqualTo(backendPath);
    }

    /** Replies with the request path (and query), plus the X-Forwarded-Prefix header it got. */
    private static HttpServer startEchoBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String query = exchange.getRequestURI().getRawQuery();
                String seen = exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query);
                byte[] body = seen.getBytes(StandardCharsets.UTF_8);
                String prefix = exchange.getRequestHeaders().getFirst("X-Forwarded-Prefix");
                exchange.getResponseHeaders().set("X-Seen-Forwarded-Prefix", prefix == null ? "none" : prefix);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
