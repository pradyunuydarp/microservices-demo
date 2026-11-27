package com.example.canteen.menu;

import com.example.canteen.menu.controller.MenuController;
import com.example.canteen.menu.dto.MenuItemResponse;
import com.example.canteen.menu.service.MenuItemNotFoundException;
import com.example.canteen.menu.service.MenuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: the controller, JSON and validation. The service is a mock. */
@WebMvcTest(MenuController.class)
class MenuControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService menuService;

    @Test
    void batchReadReturnsRequestedItems() throws Exception {
        given(menuService.findByIds(List.of(1L, 3L))).willReturn(List.of(
                new MenuItemResponse(1L, "Masala Dosa", new BigDecimal("60.00"), true),
                new MenuItemResponse(3L, "Filter Coffee", new BigDecimal("20.00"), true)));

        mvc.perform(get("/menu").param("ids", "1,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Filter Coffee"))
                .andExpect(jsonPath("$[1].price").value(20.00));
    }

    @Test
    void unknownItemIs404ProblemDetail() throws Exception {
        given(menuService.findById(99L)).willThrow(new MenuItemNotFoundException(99L));

        mvc.perform(get("/menu/99"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Menu item not found"))
                .andExpect(jsonPath("$.detail").value("Menu item 99 does not exist"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        given(menuService.create(any())).willReturn(
                new MenuItemResponse(7L, "Idli", new BigDecimal("30.00"), true));

        mvc.perform(post("/menu").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Idli", "price": 30.00, "available": true}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/menu/7"))
                .andExpect(jsonPath("$.id").value(7));
    }

    @ParameterizedTest(name = "{0} is rejected")
    @CsvSource(delimiter = '|', textBlock = """
            name      | {"name": "",     "price": 30.00, "available": true}
            price     | {"name": "Idli", "price": -1,    "available": true}
            price     | {"name": "Idli",                 "available": true}
            available | {"name": "Idli", "price": 30.00}
            """)
    void invalidRequestIs400WithFieldError(String field, String body) throws Exception {
        mvc.perform(post("/menu").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }
}
