package com.example.canteen.order.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Writes a log line whenever a circuit breaker changes state, e.g. CLOSED -> OPEN. */
@Configuration
class CircuitBreakerLogging {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLogging.class);

    @Bean
    RegistryEventConsumer<CircuitBreaker> logStateTransitions() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                event.getAddedEntry().getEventPublisher().onStateTransition(transition ->
                        log.warn("Circuit breaker '{}': {} -> {}", transition.getCircuitBreakerName(),
                                transition.getStateTransition().getFromState(),
                                transition.getStateTransition().getToState()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
            }
        };
    }
}
