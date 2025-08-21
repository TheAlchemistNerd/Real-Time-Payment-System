package com.paymentprocessor.notification.service;

import com.paymentprocessor.notification.model.NotificationMessage;

import java.util.concurrent.CompletableFuture;

public interface NotificationService {
    CompletableFuture<Boolean> sendNotification(NotificationMessage message);
}
