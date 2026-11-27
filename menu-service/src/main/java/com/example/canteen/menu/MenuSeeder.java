package com.example.canteen.menu;

import com.example.canteen.menu.domain.MenuItem;
import com.example.canteen.menu.repository.MenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Puts a few items on the menu the first time the service starts with an empty database. */
@Component
class MenuSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MenuSeeder.class);

    private final MenuItemRepository repository;

    MenuSeeder(MenuItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        List<MenuItem> items = List.of(
                new MenuItem("Masala Dosa", new BigDecimal("60.00"), true),
                new MenuItem("Veg Sandwich", new BigDecimal("45.00"), true),
                new MenuItem("Filter Coffee", new BigDecimal("20.00"), true),
                new MenuItem("Samosa", new BigDecimal("15.00"), true),
                new MenuItem("Paneer Wrap", new BigDecimal("80.00"), true),
                new MenuItem("Mango Lassi", new BigDecimal("40.00"), false)); // sold out today
        repository.saveAll(items);
        log.info("Seeded {} menu items", items.size());
    }
}
