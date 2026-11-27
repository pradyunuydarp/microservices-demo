package com.example.canteen.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Starts the real gateway and one fake backend that answers with the path it received,
 * then checks that each route reaches the right place, with /api stripped.
 * Security is not the point here (see GatewaySecurityTest), so requests carry a
 * pretend token with every scope.
 */
@SpringBootTest
@AutoConfigureWebTestClient
class ApiGatewayRoutesTest {

    static final FakeBackend backend = new FakeBackend();

    @DynamicPropertySource
    static void routeToBackend(DynamicPropertyRegistry registry) {
        registry.add("MENU_SERVICE_URL", backend::url);
        registry.add("CUSTOMER_SERVICE_URL", backend::url);
        registry.add("ORDER_SERVICE_URL", backend::url);
        registry.add("AUTH_SERVICE_URL", backend::url);
    }

    @Autowired
    WebTestClient client;

    @AfterAll
    static void stopBackend() {
        backend.stop();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            /api/menu           | /menu
            /api/menu?ids=1,2   | /menu?ids=1,2
            /api/customers/7    | /customers/7
            /api/orders         | /orders
            """)
    void routesAndStripsPrefix(String gatewayPath, String backendPath) {
        client.mutateWith(mockJwt().jwt(jwt -> jwt.claim("scope", "orders customers menu.write")))
                .get().uri(gatewayPath).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Seen-Forwarded-Prefix", "/api")
                .expectBody(String.class).isEqualTo(backendPath);
    }

    @Test
    void tokenRequestsGoToAuthServiceUnchanged() {
        client.post().uri("/oauth2/token").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("/oauth2/token");
    }
}
