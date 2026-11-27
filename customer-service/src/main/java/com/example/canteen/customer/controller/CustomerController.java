package com.example.canteen.customer.controller;

import com.example.canteen.customer.dto.CustomerResponse;
import com.example.canteen.customer.dto.RegisterCustomerRequest;
import com.example.canteen.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        CustomerResponse created = customerService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @GetMapping
    public List<CustomerResponse> list() {
        return customerService.findAll();
    }
}
