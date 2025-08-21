package com.paymentprocessor.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notification/v1")
@Tag(name = "Notification Service", description = "Notification service endpoints")
public class NotificationController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Notification Service health check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Notification service is up");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    @Operation(summary = "Service info", description = "Get service information")
    public ResponseEntity<Map<String, Object>> serviceInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "notification-service");
        info.put("version", "1.0.0");
        info.put("description", "Multi-channel notification service");
        info.put("supportedChannels", new String[]{"EMAIL", "SMS", "WEBHOOK"});
        return ResponseEntity.ok(info);
    }
}
