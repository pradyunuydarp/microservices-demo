package com.example.canteen.order.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/** A service we depend on did not answer properly. The API turns this into 503. */
public class ServiceUnavailableException extends RuntimeException {

    private final String service;

    public ServiceUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable " + (cause instanceof CallNotPermittedException
                ? "(circuit breaker is open, call not attempted)"
                : "(call failed or timed out)"), cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
