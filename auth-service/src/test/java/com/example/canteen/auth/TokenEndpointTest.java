package com.example.canteen.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real authorization server (clients from application.yml), called through MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder; // checks signatures with the server's own public key

    @Test
    void issuesSignedJwtForValidClient() throws Exception {
        // Execute: the client-credentials grant, client id and secret sent with HTTP Basic.
        String response = requestToken("canteen-app", "canteen-app-secret", "orders customers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(greaterThan(3500))) // 1 hour, see application.yml
                .andReturn().getResponse().getContentAsString();

        // Verify: the token is a JWT signed by this server, with the fixed issuer and the client's scopes.
        Jwt jwt = jwtDecoder.decode(JsonPath.read(response, "$.access_token"));
        assertThat(jwt.getIssuer()).hasToString("http://auth-service:8090");
        assertThat(jwt.getSubject()).isEqualTo("canteen-app");
        assertThat(jwt.getClaimAsStringList("scope")).containsExactlyInAnyOrder("orders", "customers");
    }

    @Test
    void adminMayAskForMenuWriteOnly() throws Exception {
        String response = requestToken("canteen-admin", "canteen-admin-secret", "menu.write")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(response, "$.access_token"));
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("menu.write"); // least privilege
    }

    @ParameterizedTest(name = "{0} / {1} -> 401 invalid_client")
    @CsvSource({
            "canteen-app,   wrong-secret",
            "unknown-app,   canteen-app-secret",
    })
    void rejectsBadClientCredentials(String clientId, String secret) throws Exception {
        requestToken(clientId, secret, "orders")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void appMayNotAskForAScopeItWasNotGiven() throws Exception {
        requestToken("canteen-app", "canteen-app-secret", "menu.write")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    @Test
    void publicKeysArePublic() throws Exception {
        mvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    /** POST /oauth2/token with grant_type=client_credentials. The scopes are space-separated. */
    private ResultActions requestToken(String clientId, String secret, String scope) throws Exception {
        return mvc.perform(post("/oauth2/token")
                .with(httpBasic(clientId, secret))
                .param("grant_type", "client_credentials")
                .param("scope", scope));
    }
}
