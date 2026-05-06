package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.core.UserCoreService;
import com.intuit.walletservice.businesslogic.core.UserCoreService.CreateUserResult;
import com.intuit.walletservice.businesslogic.core.UserNotFoundException;
import com.intuit.walletservice.businesslogic.core.UserView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(ApiExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserCoreService userCoreService;

    @Test
    void post_validBody_returns201AndBody() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        UserView view = new UserView(id, "alice@example.com", "CONSUMER", "us-east-1", now, now);
        when(userCoreService.createUser(eq("alice@example.com"), eq("CONSUMER"), eq("us-east-1")))
                .thenReturn(new CreateUserResult(view, true));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","role":"CONSUMER","homeRegion":"us-east-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intuitAccountId").value(id.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CONSUMER"))
                .andExpect(jsonPath("$.homeRegion").value("us-east-1"));
    }

    @Test
    void post_duplicateEmail_returns200() throws Exception {
        UserView view = new UserView(
                UUID.randomUUID(),
                "alice@example.com",
                "CONSUMER",
                "us-east-1",
                OffsetDateTime.now(),
                OffsetDateTime.now());
        when(userCoreService.createUser(any(), any(), any()))
                .thenReturn(new CreateUserResult(view, false));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","role":"CONSUMER","homeRegion":"us-east-1"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void post_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","role":"CONSUMER","homeRegion":"us-east-1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","role":"CONSUMER","homeRegion":"us-east-1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_missingRole_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","homeRegion":"us-east-1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_unknownRole_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","role":"ADMIN","homeRegion":"us-east-1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_blankHomeRegion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","role":"CONSUMER","homeRegion":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_existingId_returns200AndBody() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        UserView view = new UserView(id, "alice@example.com", "CONSUMER", "us-east-1", now, now);
        when(userCoreService.getUser(eq(id))).thenReturn(view);

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intuitAccountId").value(id.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CONSUMER"))
                .andExpect(jsonPath("$.homeRegion").value("us-east-1"));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userCoreService.getUser(eq(id))).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
