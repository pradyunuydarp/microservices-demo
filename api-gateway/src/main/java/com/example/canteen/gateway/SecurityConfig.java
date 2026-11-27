package com.example.canteen.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * The gateway is an OAuth 2.0 resource server. Every request to /api/** must carry
 * "Authorization: Bearer <JWT>", except reading the menu. The gateway checks the token's
 * signature (with auth-service's public keys), issuer and expiry, then its scopes.
 * No token or a bad token: 401. A good token without the needed scope: 403.
 *
 * The services behind the gateway do not check tokens again. They trust the private
 * network (perimeter security). See "2. Securing" in README.md for the trade-off.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(exchange -> exchange
                        // Rules are checked from top to bottom; the first match wins.
                        .pathMatchers(HttpMethod.POST, "/oauth2/token").permitAll() // how a client gets a token
                        .pathMatchers("/actuator/**").permitAll() // health and routes; lock down in production
                        .pathMatchers(HttpMethod.GET, "/api/menu/**").permitAll() // anyone may read the menu
                        .pathMatchers("/api/menu/**").hasAuthority("SCOPE_menu.write") // POST, PUT, DELETE
                        .pathMatchers("/api/orders/**").hasAuthority("SCOPE_orders")
                        .pathMatchers("/api/customers/**").hasAuthority("SCOPE_customers")
                        .pathMatchers("/api/**").authenticated() // any other route needs some valid token
                        .anyExchange().denyAll())
                // Check JWTs. Where the public keys are and which issuer to expect: application.yml.
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                // A token API has no browser sessions or cookies, so there is nothing for CSRF to protect.
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }
}
