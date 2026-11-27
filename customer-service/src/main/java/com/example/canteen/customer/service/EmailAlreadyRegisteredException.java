package com.example.canteen.customer.service;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("A customer with email " + email + " is already registered");
    }
}
