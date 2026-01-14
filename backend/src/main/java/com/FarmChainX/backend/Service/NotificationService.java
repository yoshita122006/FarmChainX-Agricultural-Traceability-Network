package com.FarmChainX.backend.Service;

import com.FarmChainX.backend.Model.Notification;
import com.FarmChainX.backend.Repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /* =========================================================
       CREATE
    ========================================================= */

    public Notification create(Notification notification) {
        return repo.save(notification);
    }

    /* =========================================================
       FETCH ALL NOTIFICATIONS (USER + ALL)
    ========================================================= */

    public List<Notification> getUserNotifications(String userId, String role) {
        return repo.findRelevantNotifications(userId, role);
    }

    /* =========================================================
       UNREAD COUNT (USER + ALL)
    ========================================================= */

    public long getUnreadCount(String userId, String role) {
        return repo.countUnreadNotifications(userId, role);
    }

    /* =========================================================
       MARK SINGLE AS READ
    ========================================================= */

    public void markAsRead(Long id) {
        Notification n = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        n.setRead(true);
        repo.save(n);
    }

    /* =========================================================
       MARK ALL AS READ (USER + ALL)
    ========================================================= */

    public void markAllAsRead(String userId, String role) {
        List<Notification> list =
                repo.findUnreadNotifications(userId, role);
        list.forEach(n -> n.setRead(true));
        repo.saveAll(list);
    }
}
