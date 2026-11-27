package com.example.canteen.order.service;

import com.example.canteen.order.client.CustomerClient;
import com.example.canteen.order.client.CustomerDto;
import com.example.canteen.order.client.MenuClient;
import com.example.canteen.order.client.MenuItemDto;
import com.example.canteen.order.domain.Order;
import com.example.canteen.order.domain.OrderLine;
import com.example.canteen.order.dto.OrderItemRequest;
import com.example.canteen.order.dto.OrderResponse;
import com.example.canteen.order.dto.PlaceOrderRequest;
import com.example.canteen.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final MenuClient menuClient;

    public OrderService(OrderRepository orderRepository, CustomerClient customerClient, MenuClient menuClient) {
        this.orderRepository = orderRepository;
        this.customerClient = customerClient;
        this.menuClient = menuClient;
    }

    /**
     * Deliberately NOT @Transactional: we do not hold a database transaction open
     * while we wait for other services over the network. save() runs its own short one.
     */
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        // 1. Ask customer-service: does this customer exist?
        CustomerDto customer = customerClient.findCustomer(request.customerId())
                .orElseThrow(() -> new OrderRejectedException(
                        "Customer " + request.customerId() + " does not exist"));

        // 2. Ask menu-service ONCE for all the items (a batch call, not one call per item).
        Set<Long> ids = request.items().stream()
                .map(OrderItemRequest::menuItemId)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<Long, MenuItemDto> menu = menuClient.findItems(ids).stream()
                .collect(Collectors.toMap(MenuItemDto::id, Function.identity()));

        // 3. Check every line and copy the current name and price into the order.
        Order order = new Order(customer.id());
        for (OrderItemRequest item : request.items()) {
            MenuItemDto menuItem = menu.get(item.menuItemId());
            if (menuItem == null) {
                throw new OrderRejectedException("Menu item " + item.menuItemId() + " does not exist");
            }
            if (!menuItem.available()) {
                throw new OrderRejectedException(menuItem.name() + " (menu item " + menuItem.id() + ") is not available");
            }
            order.addLine(new OrderLine(menuItem.id(), menuItem.name(), menuItem.price(), item.quantity()));
        }

        // 4. Save it in our own database.
        Order saved = orderRepository.save(order);
        log.info("Placed order {} for customer {}, total {}", saved.getId(), customer.id(), saved.total());
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll(Sort.by("id")).stream().map(OrderResponse::from).toList();
    }
}
