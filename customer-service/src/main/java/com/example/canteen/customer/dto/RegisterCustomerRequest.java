package com.example.canteen.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 200) String email) {
}
