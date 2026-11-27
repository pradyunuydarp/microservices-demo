package com.example.canteen.order.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The "downstream.*" settings from application.yml: where the other services are
 * and how long we are willing to wait for them.
 */
@ConfigurationProperties("downstream")
public record DownstreamProperties(
        String menuServiceUrl,
        String customerServiceUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
