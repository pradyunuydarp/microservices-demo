package com.example.canteen.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** Calls customer-service over REST. */
@Component
public class CustomerClient {

    static final String SERVICE = "customer-service";
    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);

    private final RestClient restClient;

    public CustomerClient(@Qualifier("customerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** GET /customers/{id}. Empty if the customer does not exist. */
    @CircuitBreaker(name = SERVICE, fallbackMethod = "unavailable")
    public Optional<CustomerDto> findCustomer(Long id) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/customers/{id}", id)
                    .retrieve()
                    .body(CustomerDto.class));
        } catch (HttpClientErrorException.NotFound notFound) {
            // 404 is a normal answer ("no such customer"), not a failure,
            // so we return instead of throwing and the circuit breaker does not count it.
            return Optional.empty();
        }
    }

    /** Resilience4j calls this instead when the call above throws or the circuit is open. */
    private Optional<CustomerDto> unavailable(Long id, Throwable cause) {
        log.warn("{} call failed: {}", SERVICE, cause.toString());
        throw new ServiceUnavailableException(SERVICE, cause);
    }
}
