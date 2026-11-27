package com.example.canteen.customer.repository;

import com.example.canteen.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Spring Data builds the query from the method name: ... WHERE upper(email) = upper(?)
    boolean existsByEmailIgnoreCase(String email);
}
