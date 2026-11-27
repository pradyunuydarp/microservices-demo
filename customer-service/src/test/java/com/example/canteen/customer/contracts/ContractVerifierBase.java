package com.example.canteen.customer.contracts;

import com.example.canteen.customer.controller.CustomerController;
import com.example.canteen.customer.dto.CustomerResponse;
import com.example.canteen.customer.service.CustomerNotFoundException;
import com.example.canteen.customer.service.CustomerService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;

/**
 * Producer side of the contracts in src/test/resources/contracts. Spring Cloud Contract
 * generates one test per contract (see target/generated-test-sources/contracts) and makes
 * it extend this class. The web layer is real (controller, JSON, the 404 problem detail);
 * CustomerService is a mock that knows customer 1 and not customer 999.
 */
@WebMvcTest(CustomerController.class)
public abstract class ContractVerifierBase {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CustomerService customerService;

    @BeforeEach
    void setUp() {
        given(customerService.findById(1L)).willReturn(new CustomerResponse(1L, "Asha", "asha@campus.edu"));
        given(customerService.findById(999L)).willThrow(new CustomerNotFoundException(999L));
        RestAssuredMockMvc.mockMvc(mvc);
    }
}
