package com.example.canteen.order;

import com.example.canteen.order.client.CustomerClient;
import com.example.canteen.order.client.CustomerDto;
import com.example.canteen.order.client.MenuClient;
import com.example.canteen.order.client.MenuItemDto;
import com.example.canteen.order.domain.Order;
import com.example.canteen.order.dto.OrderItemRequest;
import com.example.canteen.order.dto.OrderResponse;
import com.example.canteen.order.dto.PlaceOrderRequest;
import com.example.canteen.order.repository.OrderRepository;
import com.example.canteen.order.service.OrderRejectedException;
import com.example.canteen.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Order placement rules, with the other services replaced by mocks. No Spring, no HTTP. */
class OrderServiceTest {

    CustomerClient customerClient = mock(CustomerClient.class);
    MenuClient menuClient = mock(MenuClient.class);
    OrderRepository orderRepository = mock(OrderRepository.class);
    OrderService orderService = new OrderService(orderRepository, customerClient, menuClient);

    @BeforeEach
    void setUp() {
        given(customerClient.findCustomer(1L)).willReturn(Optional.of(new CustomerDto(1L, "Asha")));
        given(customerClient.findCustomer(42L)).willReturn(Optional.empty());
        given(menuClient.findItems(anyCollection())).willReturn(List.of(
                new MenuItemDto(1L, "Masala Dosa", new BigDecimal("60.00"), true),
                new MenuItemDto(3L, "Filter Coffee", new BigDecimal("20.00"), true),
                new MenuItemDto(6L, "Mango Lassi", new BigDecimal("40.00"), false)));
        given(orderRepository.save(any(Order.class))).willAnswer(call -> call.getArgument(0));
    }

    @Test
    void placesOrderWithPriceSnapshotAndTotal() {
        OrderResponse order = orderService.placeOrder(new PlaceOrderRequest(1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(3L, 1))));

        assertThat(order.customerId()).isEqualTo(1L);
        assertThat(order.lines()).extracting(OrderResponse.Line::name).containsExactly("Masala Dosa", "Filter Coffee");
        assertThat(order.total()).isEqualByComparingTo("140.00"); // 2 x 60 + 1 x 20
        verify(menuClient).findItems(Set.of(1L, 3L)); // one batch call for both items
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource(delimiter = '|', textBlock = """
            42 | 1  | Customer 42 does not exist
            1  | 99 | Menu item 99 does not exist
            1  | 6  | Mango Lassi (menu item 6) is not available
            """)
    void rejectsOrder(long customerId, long menuItemId, String expectedMessage) {
        PlaceOrderRequest request = new PlaceOrderRequest(customerId, List.of(new OrderItemRequest(menuItemId, 1)));

        assertThatThrownBy(() -> orderService.placeOrder(request))
                .isInstanceOf(OrderRejectedException.class)
                .hasMessage(expectedMessage);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unknownCustomerStopsBeforeCallingMenuService() {
        PlaceOrderRequest request = new PlaceOrderRequest(42L, List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.placeOrder(request)).isInstanceOf(OrderRejectedException.class);
        verify(menuClient, never()).findItems(anyCollection());
    }
}
