package com.FarmChainX.backend.Service;

import com.FarmChainX.backend.Model.Notification;
import com.FarmChainX.backend.Repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationEventService {

    private final NotificationRepository repo;

    public NotificationEventService(NotificationRepository repo) {
        this.repo = repo;
    }

    public void notifyUser(
            String userId,
            String role,
            String title,
            String message,
            String type,
            String entityId
    ) {
        if (userId == null || userId.isEmpty()) return;

        Notification n = new Notification();
        n.setUserId(userId);
        n.setUserRole(role);
        n.setTitle(title);
        n.setMessage(message);
        n.setNotificationType(type);
        n.setEntityId(entityId);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());

        repo.save(n);
    }
}
