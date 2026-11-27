package com.example.canteen.menu.contracts;

import com.example.canteen.menu.controller.MenuController;
import com.example.canteen.menu.dto.MenuItemResponse;
import com.example.canteen.menu.service.MenuService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;

/**
 * Producer side of the contracts in src/test/resources/contracts. Spring Cloud Contract
 * generates one test per contract (see target/generated-test-sources/contracts) and makes
 * it extend this class. The test sends the contract's request to the real MenuController
 * and checks the answer against the contract's response.
 *
 * This class only provides the data the contracts talk about. The web layer (controller,
 * JSON, errors) is real; MenuService is a mock.
 */
@WebMvcTest(MenuController.class)
public abstract class ContractVerifierBase {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MenuService menuService;

    @BeforeEach
    void setUp() {
        given(menuService.findByIds(List.of(1L, 3L))).willReturn(List.of(
                new MenuItemResponse(1L, "Masala Dosa", new BigDecimal("60.00"), true),
                new MenuItemResponse(3L, "Filter Coffee", new BigDecimal("20.00"), true)));
        RestAssuredMockMvc.mockMvc(mvc);
    }
}
