package com.example.canteen.customer.service;

import com.example.canteen.customer.domain.Customer;
import com.example.canteen.customer.dto.CustomerResponse;
import com.example.canteen.customer.dto.RegisterCustomerRequest;
import com.example.canteen.customer.repository.CustomerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse register(RegisterCustomerRequest request) {
        // The unique column in the database is the real guard; this check gives a friendly 409.
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }
        Customer saved = repository.save(new Customer(request.name(), request.email()));
        return CustomerResponse.from(saved);
    }

    public CustomerResponse findById(Long id) {
        return repository.findById(id)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    public List<CustomerResponse> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(CustomerResponse::from).toList();
    }
}
