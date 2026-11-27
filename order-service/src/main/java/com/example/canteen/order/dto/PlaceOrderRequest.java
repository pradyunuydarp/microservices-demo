package com.example.canteen.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {"customerId": 1, "items": [{"menuItemId": 1, "quantity": 2}]} */
public record PlaceOrderRequest(
        @NotNull @Positive Long customerId,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid OrderItemRequest> items) {
}
