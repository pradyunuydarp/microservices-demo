package com.example.canteen.order;

import com.example.canteen.order.client.CustomerClient;
import com.example.canteen.order.client.CustomerDto;
import com.example.canteen.order.client.MenuClient;
import com.example.canteen.order.client.MenuItemDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consumer side of the contracts. The real MenuClient and CustomerClient (RestClient,
 * timeouts, circuit breakers) call WireMock servers that Stub Runner starts from the stubs
 * menu-service and customer-service generated from their contracts. If a provider changes
 * its contract, the stubs change and these tests show what breaks in order-service.
 *
 * Lifecycle: Setup = Stub Runner starts one WireMock server per stubs jar before the tests;
 * Execute and Verify = each test; Teardown = Stub Runner stops the servers after the tests.
 */
@SpringBootTest(properties = {
        "downstream.menu-service-url=http://localhost:18081",
        "downstream.customer-service-url=http://localhost:18082"})
// group:artifact:version:classifier:port. CLASSPATH: find the stubs jars on the test classpath.
@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.CLASSPATH, ids = {
        "com.example.canteen:menu-service:+:stubs:18081",
        "com.example.canteen:customer-service:+:stubs:18082"})
@AutoConfigureMockMvc
class ConsumerContractTest {

    @Autowired
    MenuClient menuClient;
    @Autowired
    CustomerClient customerClient;
    @Autowired
    MockMvc mvc;

    @Test
    void menuClientReadsItemsInOneBatch() {
        List<MenuItemDto> items = menuClient.findItems(List.of(1L, 3L)); // Execute

        assertThat(items).containsExactly( // Verify
                new MenuItemDto(1L, "Masala Dosa", new BigDecimal("60.0"), true),
                new MenuItemDto(3L, "Filter Coffee", new BigDecimal("20.0"), true));
    }

    @Test
    void customerClientReadsCustomer() {
        assertThat(customerClient.findCustomer(1L)).contains(new CustomerDto(1L, "Asha"));
    }

    @Test
    void customerClientTreatsUnknownCustomerAsEmpty() {
        assertThat(customerClient.findCustomer(999L)).isEmpty(); // the 404 contract
    }

    /** A component test: all of order-service, with both providers replaced by contract stubs. */
    @Test
    void placesOrderAgainstTheStubs() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 1, "items": [{"menuItemId": 1, "quantity": 2}, {"menuItemId": 3, "quantity": 1}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[1].name").value("Filter Coffee"))
                .andExpect(jsonPath("$.total").value(140.0));
    }
}
