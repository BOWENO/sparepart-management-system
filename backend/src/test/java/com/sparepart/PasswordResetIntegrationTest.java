package com.sparepart;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:password-reset-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void supervisorCanResetPasswordAndWarehouseAccountCannotOpenAccountManagement() throws Exception {
        MockHttpSession supervisor = login("viewer-local", "viewer123");
        mvc.perform(get("/api/v1/user-accounts").session(supervisor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());

        mvc.perform(post("/api/v1/user-accounts/3/reset-password").session(supervisor)
                        .contentType("application/json").content("{\"temporaryPassword\":\"Temp2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"viewer2-local\",\"password\":\"Temp2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.needChangePassword").value(true));

        MockHttpSession warehouse = login("test", "test");
        mvc.perform(get("/api/v1/user-accounts").session(warehouse)).andExpect(status().isForbidden());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
