package com.example.canteen.order.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An order. Note that it stores only the customer's id, not a foreign key into another
 * database: customer-service owns customers, and we reach it through its API.
 */
@Entity
@Table(name = "orders") // ORDER is a reserved word in SQL
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Instant placedAt;

    @ElementCollection
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_number")
    private List<OrderLine> lines = new ArrayList<>();

    protected Order() {
        // JPA needs a no-argument constructor.
    }

    public Order(Long customerId) {
        this.customerId = customerId;
        this.placedAt = Instant.now();
    }

    public void addLine(OrderLine line) {
        lines.add(line);
    }

    public BigDecimal total() {
        return lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
