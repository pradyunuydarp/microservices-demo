package com.example.canteen.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole application (controller, service, JPA, H2) wired together. */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerServiceApplicationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void registerThenFetch() throws Exception {
        String body = """
                {"name": "Ravi", "email": "ravi@campus.edu"}""";

        // The headers the API gateway adds: the Location we get back must point at the gateway.
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Forwarded-Host", "localhost:8080")
                        .header("X-Forwarded-Prefix", "/api"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/api/customers/1"));
        mvc.perform(get("/customers"))
                .andExpect(jsonPath("$[0].email").value("ravi@campus.edu"));
        // Same email again (different case) is a conflict.
        mvc.perform(post("/customers").contentType(MediaType.APPLICATION_JSON).content(body.replace("ravi@", "RAVI@")))
                .andExpect(status().isConflict());
    }
}
