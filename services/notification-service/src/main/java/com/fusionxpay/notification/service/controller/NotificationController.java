package com.fusionxpay.notification.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/create-notification") 
public class NotificationController {

    @GetMapping
    public String notificationEndpoint() {
        return "Hello from Notification Service!!";
    }
}