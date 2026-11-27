package com.example.canteen.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Who may call what through the gateway. The real gateway and its SecurityConfig, with a
 * fake backend behind it. No auth-service is needed:
 * <ul>
 *   <li>{@code mockJwt()} stands in for a valid token with the scopes we choose, and</li>
 *   <li>for the signature and issuer checks we sign real JWTs with a test key, which the
 *       fake backend publishes at /oauth2/jwks, where the gateway looks for auth-service's keys.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    static final FakeBackend backend = new FakeBackend();
    static final RSAKey signingKey = newRsaKey();

    @DynamicPropertySource
    static void routeToBackend(DynamicPropertyRegistry registry) {
        backend.serveJson("/oauth2/jwks", new JWKSet(signingKey.toPublicJWK()).toString());
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

    @ParameterizedTest(name = "{0} {1} with scopes [{2}] -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            GET    | /api/menu        | no token             | 200
            GET    | /api/menu?ids=1  | no token             | 200
            POST   | /api/menu        | no token             | 401
            POST   | /api/menu        | orders customers     | 403
            POST   | /api/menu        | menu.write           | 200
            PUT    | /api/menu/1      | orders               | 403
            DELETE | /api/menu/1      | menu.write           | 200
            GET    | /api/orders      | no token             | 401
            POST   | /api/orders      | customers            | 403
            POST   | /api/orders      | orders               | 200
            GET    | /api/customers/1 | orders               | 403
            GET    | /api/customers/1 | customers            | 200
            GET    | /api/reviews     | no token             | 401
            POST   | /oauth2/token    | no token             | 200
            GET    | /actuator/health | no token             | 200
            """)
    void checksScopes(HttpMethod method, String path, String scopes, int expectedStatus) {
        WebTestClient caller = scopes.equals("no token") ? client
                : client.mutateWith(mockJwt().jwt(jwt -> jwt.claim("scope", scopes)));

        caller.method(method).uri(path).exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            signed by auth-service's key    | 200
            from another issuer             | 401
            expired                         | 401
            signed by some other key        | 401
            not a JWT at all                | 401
            """)
    void checksTheTokenItself(String token, int expectedStatus) throws JOSEException {
        String issuer = "http://auth-service:8090"; // the same fixed issuer as in application.yml
        Instant expires = Instant.now().plus(Duration.ofMinutes(5));
        String bearer = switch (token) {
            case "signed by auth-service's key" -> sign(signingKey, issuer, expires);
            case "from another issuer" -> sign(signingKey, "http://evil.example", expires);
            case "expired" -> sign(signingKey, issuer, Instant.now().minus(Duration.ofMinutes(5)));
            case "signed by some other key" -> sign(newRsaKey(), issuer, expires);
            default -> "not-a-jwt";
        };

        client.get().uri("/api/orders")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }

    private static String sign(RSAKey key, String issuer, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("canteen-app")
                .claim("scope", "orders")
                .issueTime(Date.from(expiresAt.minus(Duration.ofHours(1))))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey newRsaKey() {
        try {
            return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
