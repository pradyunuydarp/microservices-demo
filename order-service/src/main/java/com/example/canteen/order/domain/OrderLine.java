package com.example.canteen.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * One line of an order. Name and price are COPIED from menu-service when the order is placed.
 * If the canteen later changes the price or deletes the item, old orders stay correct, and
 * order-service never has to ask menu-service about the past.
 */
@Embeddable
public record OrderLine(
        @Column(nullable = false) Long menuItemId,
        @Column(nullable = false, length = 100) String name,
        @Column(nullable = false, precision = 10, scale = 2) BigDecimal unitPrice,
        @Column(nullable = false) int quantity) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
