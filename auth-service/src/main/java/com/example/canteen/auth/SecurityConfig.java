package com.example.canteen.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two filter chains. The first handles the OAuth 2.0 endpoints, the second everything else.
 *
 * The clients, their secrets and scopes are not here: Spring Boot reads them from
 * spring.security.oauth2.authorizationserver in application.yml. Spring Boot also creates
 * the RSA key pair that signs the tokens (a new one at every start).
 */
@Configuration
class SecurityConfig {

    /**
     * The OAuth 2.0 endpoints, all provided by Spring Security:
     * POST /oauth2/token (a client trades its id and secret for a token),
     * GET /oauth2/jwks (the public keys that check the token signatures) and
     * GET /.well-known/oauth-authorization-server (metadata: where everything is).
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServer(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(authorizationServer ->
                        http.securityMatcher(authorizationServer.getEndpointsMatcher()))
                // The token endpoint first authenticates the client (HTTP Basic: client id + secret).
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
        return http.build();
    }

    /** Everything else: only the health check is open (Docker Compose uses it). */
    @Bean
    @Order(2)
    SecurityFilterChain everythingElse(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
}
