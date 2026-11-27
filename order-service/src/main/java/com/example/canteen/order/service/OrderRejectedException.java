package com.example.canteen.order.service;

/** The request was well-formed but cannot be accepted (unknown customer, sold-out item, ...). API: 422. */
public class OrderRejectedException extends RuntimeException {

    public OrderRejectedException(String message) {
        super(message);
    }
}
