package com.example.canteen.order;

import com.example.canteen.order.client.ServiceUnavailableException;
import com.example.canteen.order.controller.OrderController;
import com.example.canteen.order.dto.OrderResponse;
import com.example.canteen.order.service.OrderNotFoundException;
import com.example.canteen.order.service.OrderRejectedException;
import com.example.canteen.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    static final String VALID_ORDER = """
            {"customerId": 1, "items": [{"menuItemId": 1, "quantity": 2}]}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orderService;

    @Test
    void placeOrderReturns201WithLocation() throws Exception {
        given(orderService.placeOrder(any())).willReturn(new OrderResponse(5L, 1L, Instant.now(),
                List.of(new OrderResponse.Line(1L, "Masala Dosa", new BigDecimal("60.00"), 2, new BigDecimal("120.00"))),
                new BigDecimal("120.00")));

        postOrder(VALID_ORDER)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/orders/5"))
                .andExpect(jsonPath("$.total").value(120.00));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            customerId        | {"items": [{"menuItemId": 1, "quantity": 1}]}
            items             | {"customerId": 1, "items": []}
            items[0].quantity | {"customerId": 1, "items": [{"menuItemId": 1, "quantity": 0}]}
            items[0].menuItemId | {"customerId": 1, "items": [{"quantity": 1}]}
            """)
    void invalidOrderIs400(String field, String body) throws Exception {
        postOrder(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['" + field + "']").exists());
    }

    @Test
    void rejectedOrderIs422() throws Exception {
        given(orderService.placeOrder(any())).willThrow(new OrderRejectedException("Customer 42 does not exist"));

        postOrder(VALID_ORDER)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Customer 42 does not exist"));
    }

    @Test
    void unavailableDependencyIs503() throws Exception {
        given(orderService.placeOrder(any()))
                .willThrow(new ServiceUnavailableException("menu-service", new RuntimeException("boom")));

        postOrder(VALID_ORDER)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.service").value("menu-service"));
    }

    @Test
    void unknownOrderIs404() throws Exception {
        given(orderService.findById(9L)).willThrow(new OrderNotFoundException(9L));

        mvc.perform(get("/orders/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }

    private ResultActions postOrder(String body) throws Exception {
        return mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
