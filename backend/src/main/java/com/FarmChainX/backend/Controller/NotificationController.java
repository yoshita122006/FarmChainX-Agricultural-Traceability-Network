package com.FarmChainX.backend.Controller;

import com.FarmChainX.backend.Service.NotificationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:4200")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/{userId}/{role}")
    public Object getUserNotifications(
            @PathVariable String userId,
            @PathVariable String role) {
        return service.getUserNotifications(userId, role);
    }

    @GetMapping("/{userId}/{role}/count")
    public long getUnreadCount(
            @PathVariable String userId,
            @PathVariable String role) {
        return service.getUnreadCount(userId, role);
    }

    @PutMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id) {
        service.markAsRead(id);
    }

    @PutMapping("/{userId}/{role}/read-all")
    public void markAllAsRead(
            @PathVariable String userId,
            @PathVariable String role) {
        service.markAllAsRead(userId, role);
    }
}
