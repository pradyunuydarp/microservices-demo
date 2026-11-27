package com.example.canteen.customer;

import com.example.canteen.customer.controller.CustomerController;
import com.example.canteen.customer.dto.CustomerResponse;
import com.example.canteen.customer.service.CustomerNotFoundException;
import com.example.canteen.customer.service.CustomerService;
import com.example.canteen.customer.service.EmailAlreadyRegisteredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CustomerService customerService;

    @Test
    void registerReturns201WithLocation() throws Exception {
        given(customerService.register(any())).willReturn(new CustomerResponse(1L, "Asha", "asha@campus.edu"));

        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha", "email": "asha@campus.edu"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/customers/1"))
                .andExpect(jsonPath("$.name").value("Asha"));
    }

    @Test
    void getReturnsCustomer() throws Exception {
        given(customerService.findById(1L)).willReturn(new CustomerResponse(1L, "Asha", "asha@campus.edu"));

        mvc.perform(get("/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@campus.edu"));
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        given(customerService.findById(42L)).willThrow(new CustomerNotFoundException(42L));

        mvc.perform(get("/customers/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Customer 42 does not exist"));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        given(customerService.register(any())).willThrow(new EmailAlreadyRegisteredException("asha@campus.edu"));

        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Asha", "email": "asha@campus.edu"}"""))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest(name = "{0} is rejected")
    @CsvSource(delimiter = '|', textBlock = """
            name  | {"name": "",     "email": "asha@campus.edu"}
            email | {"name": "Asha", "email": "not-an-email"}
            email | {"name": "Asha"}
            """)
    void invalidRequestIs400WithFieldError(String field, String body) throws Exception {
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }
}
