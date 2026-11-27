package com.example.canteen.menu.service;

public class MenuItemNotFoundException extends RuntimeException {

    public MenuItemNotFoundException(Long id) {
        super("Menu item " + id + " does not exist");
    }
}
