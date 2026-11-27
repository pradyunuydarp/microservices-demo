package com.example.canteen.customer.service;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long id) {
        super("Customer " + id + " does not exist");
    }
}
