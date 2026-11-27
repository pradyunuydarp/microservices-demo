package com.example.canteen.order.dto;

import com.example.canteen.order.domain.Order;
import com.example.canteen.order.domain.OrderLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, Long customerId, Instant placedAt, List<Line> lines, BigDecimal total) {

    public record Line(Long menuItemId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {

        static Line from(OrderLine line) {
            return new Line(line.menuItemId(), line.name(), line.unitPrice(), line.quantity(), line.lineTotal());
        }
    }

    public static OrderResponse from(Order order) {
        List<Line> lines = order.getLines().stream().map(Line::from).toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getPlacedAt(), lines, order.total());
    }
}
