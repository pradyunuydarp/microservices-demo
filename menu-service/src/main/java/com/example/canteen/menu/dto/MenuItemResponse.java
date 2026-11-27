package com.example.canteen.menu.dto;

import com.example.canteen.menu.domain.MenuItem;

import java.math.BigDecimal;

/**
 * What the API returns. A separate record (not the entity) so the database
 * schema can change without breaking clients.
 */
public record MenuItemResponse(Long id, String name, BigDecimal price, boolean available) {

    public static MenuItemResponse from(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getName(), item.getPrice(), item.isAvailable());
    }
}
