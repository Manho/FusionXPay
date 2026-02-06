package com.fusionxpay.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("Create notification endpoint persists message")
    void createNotification() throws Exception {
        NotificationMessage message = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Notification content")
                .recipient("user@example.com")
                .eventType("PAYMENT_CONFIRMATION")
                .build();

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(message)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Notification content"));
    }

    @Test
    @DisplayName("Get notification by ID returns 200 when found")
    void getNotificationById() throws Exception {
        NotificationMessage message = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Notification content")
                .recipient("user@example.com")
                .eventType("PAYMENT_CONFIRMATION")
                .build();

        NotificationMessage saved = notificationRepository.save(message);

        mockMvc.perform(get("/api/v1/notifications/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.content").value("Notification content"));
    }

    @Test
    @DisplayName("Get notification by ID returns 404 when missing")
    void getNotificationById_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Delete notification removes record")
    void deleteNotification() throws Exception {
        NotificationMessage message = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Delete notification")
                .recipient("user@example.com")
                .eventType("PAYMENT_CONFIRMATION")
                .build();

        NotificationMessage saved = notificationRepository.save(message);

        mockMvc.perform(delete("/api/v1/notifications/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/" + saved.getId()))
                .andExpect(status().isNotFound());
    }
}
