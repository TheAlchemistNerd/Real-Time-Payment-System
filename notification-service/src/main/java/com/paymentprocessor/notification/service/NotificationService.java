package com.paymentprocessor.notification.service;

import com.paymentprocessor.common.model.NotificationType;
import com.paymentprocessor.notification.model.NotificationMessage;

import java.util.concurrent.CompletableFuture;

public interface NotificationService {
    NotificationType channel();

    CompletableFuture<Boolean> sendNotification(NotificationMessage message);
}
