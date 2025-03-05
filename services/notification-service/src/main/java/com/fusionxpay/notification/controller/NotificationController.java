package com.fusionxpay.notification.controller;

import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification", description = "Notification management APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Create a new notification", description = "Creates a notification message in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Notification accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping
    public ResponseEntity<Void> createNotification(@RequestBody NotificationMessage notificationMessage) {
        log.info("Creating notification: {}", notificationMessage);
        notificationService.createNotification(notificationMessage);
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @Operation(summary = "Get all notifications", description = "Retrieves a list of all notification messages")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation", 
                    content = @Content(mediaType = "application/json", 
                    schema = @Schema(implementation = NotificationMessage.class)))
    })
    @GetMapping
    public ResponseEntity<List<NotificationMessage>> getAllNotifications() {
        List<NotificationMessage> notifications = notificationService.getAllNotifications();
        return ResponseEntity.ok(notifications);
    }

    @Operation(summary = "Get notification by ID", description = "Retrieves a notification message by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NotificationMessage> getNotificationById(@PathVariable Long id) {
        return notificationService.getNotificationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete notification", description = "Deletes a notification message by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Notification successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }
}