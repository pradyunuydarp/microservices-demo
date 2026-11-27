package com.example.canteen.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;

/**
 * A dish or drink the canteen sells. This table lives in menu-service's own database;
 * no other service reads it directly (pattern: Database per service).
 */
@Entity
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Money is BigDecimal, never double: 0.1 + 0.2 must equal 0.3.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean available;

    protected MenuItem() {
        // JPA needs a no-argument constructor.
    }

    public MenuItem(String name, BigDecimal price, boolean available) {
        update(name, price, available);
    }

    public void update(String name, BigDecimal price, boolean available) {
        this.name = name;
        this.price = price;
        this.available = available;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isAvailable() {
        return available;
    }
}
