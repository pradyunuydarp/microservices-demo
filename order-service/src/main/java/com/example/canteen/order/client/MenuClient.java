package com.example.canteen.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Calls menu-service over REST. */
@Component
public class MenuClient {

    static final String SERVICE = "menu-service";
    private static final Logger log = LoggerFactory.getLogger(MenuClient.class);

    private final RestClient restClient;

    public MenuClient(@Qualifier("menuRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * GET /menu?ids=1,2,3 - one request for all items, instead of one request per item.
     * Ids that do not exist are simply missing from the result.
     */
    @CircuitBreaker(name = SERVICE, fallbackMethod = "unavailable")
    public List<MenuItemDto> findItems(Collection<Long> ids) {
        String idList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        MenuItemDto[] items = restClient.get()
                .uri("/menu?ids={ids}", idList)
                .accept(MediaType.APPLICATION_JSON) // part of the contract with menu-service
                .retrieve()
                .body(MenuItemDto[].class);
        return items == null ? List.of() : List.of(items);
    }

    /** Resilience4j calls this instead when the call above throws or the circuit is open. */
    private List<MenuItemDto> unavailable(Collection<Long> ids, Throwable cause) {
        log.warn("{} call failed: {}", SERVICE, cause.toString());
        throw new ServiceUnavailableException(SERVICE, cause);
    }
}
