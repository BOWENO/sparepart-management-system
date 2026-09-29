package com.sparepart;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:sparepart-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void roleChangeTakesEffectForExistingSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"test\",\"password\":\"test\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        String originalRole = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE username='test'", String.class);
        try {
            jdbcTemplate.update("UPDATE users SET role='ROLE_VIEWER' WHERE username='test'");
            mvc.perform(get("/api/v1/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("ROLE_VIEWER"));
            mvc.perform(post("/api/v1/flows/in").session(session)
                            .param("partId", "1").param("qty", "1"))
                    .andExpect(status().isForbidden());
        } finally {
            jdbcTemplate.update("UPDATE users SET role=? WHERE username='test'", originalRole);
        }
    }

    @Test
    void forcesInitialPasswordChangeBeforeBusinessAccess() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin-local\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.needChangePassword").value(true))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/v1/parts").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("请先修改初始密码"));

        mvc.perform(post("/api/v1/auth/change-password").session(session)
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"admin123\",\"newPassword\":\"Secure@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/parts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void healthIsPublicAndChecksDatabase() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"));
    }

    @Test
    void testAccountCanAccessBusinessWithoutChangingPassword() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"test\",\"password\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.needChangePassword").value(false))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/v1/parts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
