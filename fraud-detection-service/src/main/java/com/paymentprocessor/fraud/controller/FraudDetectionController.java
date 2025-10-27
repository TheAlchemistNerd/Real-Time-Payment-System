package com.paymentprocessor.fraud.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Fraud Detection Health", description = "Health check endpoints")
public class FraudDetectionController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Fraud Detection Service health check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Fraud Detection Service");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    @Operation(summary = "Service info", description = "Get service information")
    public ResponseEntity<Map<String, Object>> serviceInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "fraud-detection-service");
        info.put("version", "1.0.0");
        info.put("description", "Real-time fraud detection with external API integration");
        return ResponseEntity.ok(info);
    }
}