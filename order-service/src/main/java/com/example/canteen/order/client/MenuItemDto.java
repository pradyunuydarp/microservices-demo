package com.example.canteen.order.client;

import java.math.BigDecimal;

/** Our own copy of the fields we need from menu-service's JSON. */
public record MenuItemDto(Long id, String name, BigDecimal price, boolean available) {
}
