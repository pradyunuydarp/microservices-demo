package com.example.canteen.menu.service;

import com.example.canteen.menu.domain.MenuItem;
import com.example.canteen.menu.dto.MenuItemRequest;
import com.example.canteen.menu.dto.MenuItemResponse;
import com.example.canteen.menu.repository.MenuItemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Business logic. Controllers stay thin; this class knows nothing about HTTP. */
@Service
@Transactional(readOnly = true)
public class MenuService {

    private final MenuItemRepository repository;

    public MenuService(MenuItemRepository repository) {
        this.repository = repository;
    }

    public List<MenuItemResponse> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(MenuItemResponse::from).toList();
    }

    /** Batch read: one query for many ids. Ids that do not exist are simply left out. */
    public List<MenuItemResponse> findByIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream()
                .sorted(Comparator.comparing(MenuItem::getId))
                .map(MenuItemResponse::from)
                .toList();
    }

    public MenuItemResponse findById(Long id) {
        return MenuItemResponse.from(load(id));
    }

    @Transactional
    public MenuItemResponse create(MenuItemRequest request) {
        MenuItem item = new MenuItem(request.name(), request.price(), request.available());
        return MenuItemResponse.from(repository.save(item));
    }

    @Transactional
    public MenuItemResponse update(Long id, MenuItemRequest request) {
        MenuItem item = load(id);
        item.update(request.name(), request.price(), request.available());
        return MenuItemResponse.from(item); // JPA saves the change when the transaction commits
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(load(id));
    }

    private MenuItem load(Long id) {
        return repository.findById(id).orElseThrow(() -> new MenuItemNotFoundException(id));
    }
}
