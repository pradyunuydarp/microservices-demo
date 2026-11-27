package com.example.canteen.order.client;

/**
 * Our own copy of the fields we need from customer-service's JSON. We do not share
 * classes with other services; unknown JSON fields are ignored (a "tolerant reader").
 */
public record CustomerDto(Long id, String name) {
}
