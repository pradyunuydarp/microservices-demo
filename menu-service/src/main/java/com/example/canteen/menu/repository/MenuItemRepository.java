package com.example.canteen.menu.repository;

import com.example.canteen.menu.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the implementation for us: findAll, findById, findAllById, save, delete, ...
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
}
