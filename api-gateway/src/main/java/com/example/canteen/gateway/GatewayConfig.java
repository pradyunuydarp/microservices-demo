package com.example.canteen.gateway;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class GatewayConfig {

    /**
     * Forget DNS answers after 5 seconds. Docker's DNS says "cache this for 10 minutes",
     * but a container that is recreated (docker compose up --build menu-service) usually
     * gets a new IP address. With the long cache the gateway would keep calling the old one.
     */
    @Bean
    HttpClientCustomizer shortDnsCache() {
        return httpClient -> httpClient.resolver(dns -> dns.cacheMaxTimeToLive(Duration.ofSeconds(5)));
    }
}
