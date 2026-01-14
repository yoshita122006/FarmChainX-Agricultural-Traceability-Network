package com.FarmChainX.backend.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.FarmChainX.backend.Model.Notification;
import com.FarmChainX.backend.Model.SupportTicket;
import com.FarmChainX.backend.Model.TicketMessage;
import com.FarmChainX.backend.Model.User;
import com.FarmChainX.backend.Repository.NotificationRepository;
import com.FarmChainX.backend.Repository.SupportTicketRepository;
import com.FarmChainX.backend.Repository.TicketMessageRepository;
import com.FarmChainX.backend.Repository.UserRepository;

@Service
public class SupportService {

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private TicketMessageRepository messageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    // ======================================================
    // TICKET CREATION
    // ======================================================

    @Transactional
    public SupportTicket createTicket(SupportTicket ticket) {

        ticket.setTicketId(
                "TKT-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000)
        );

        SupportTicket savedTicket = ticketRepository.save(ticket);

        TicketMessage initialMessage = new TicketMessage();
        initialMessage.setTicket(savedTicket);
        initialMessage.setSenderId(ticket.getReportedById());
        initialMessage.setSenderRole(ticket.getReportedByRole());
        initialMessage.setMessage("Issue reported: " + ticket.getDescription());
        initialMessage.setAdminResponse(false);
        initialMessage.setCreatedAt(LocalDateTime.now());

        // Reporter message → visible ONLY to admin
        initialMessage.setVisibleTo("ADMIN");

        messageRepository.save(initialMessage);

        createAdminNotification(savedTicket);
        return savedTicket;
    }

    private void createAdminNotification(SupportTicket ticket) {
        Notification notification = new Notification();
        notification.setUserId("1"); // default admin
        notification.setUserRole("ADMIN");
        notification.setTitle("New Support Ticket Created");
        notification.setMessage(
                "User " + ticket.getReportedByRole() +
                        " (ID: " + ticket.getReportedById() +
                        ") created ticket: " + ticket.getSubject()
        );
        notification.setNotificationType("TICKET_CREATED");
        notification.setRelatedTicketId(ticket.getTicketId());
        notification.setEntityId(String.valueOf(ticket.getId()));
        notification.setRead(false);

        notificationRepository.save(notification);
    }

    // ======================================================
    // TICKET FETCHING
    // ======================================================

    public List<SupportTicket> getUserTickets(String userId) {
        return ticketRepository.findByReportedById(userId);
    }

    public List<SupportTicket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public List<SupportTicket> getOpenTickets() {
        return ticketRepository.findByStatus("OPEN");
    }

    public SupportTicket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
    }

    public SupportTicket getTicketByTicketId(String ticketId) {
        SupportTicket ticket = ticketRepository.findByTicketId(ticketId);
        if (ticket == null) {
            throw new RuntimeException("Ticket not found");
        }
        return ticket;
    }

    // ======================================================
    // TICKET UPDATES
    // ======================================================

    public SupportTicket updateTicketStatus(Long ticketId, String status) {
        SupportTicket ticket = getTicketById(ticketId);
        ticket.setStatus(status);
        ticket.setUpdatedAt(LocalDateTime.now());
        return ticketRepository.save(ticket);
    }

    public SupportTicket updateTicketPriority(Long ticketId, String priority) {
        SupportTicket ticket = getTicketById(ticketId);
        ticket.setPriority(priority);
        ticket.setUpdatedAt(LocalDateTime.now());
        return ticketRepository.save(ticket);
    }

    // ======================================================
    // MESSAGE HANDLING (ROLE SAFE)
    // ======================================================

    @Transactional
    public TicketMessage addMessageToTicket(Long ticketId, TicketMessage message) {

        SupportTicket ticket = getTicketById(ticketId);

        String senderRole = message.getSenderRole().toUpperCase();
        message.setSenderRole(senderRole);

        boolean isAdmin = "ADMIN".equals(senderRole);
        message.setAdminResponse(isAdmin);

        message.setTicket(ticket);
        message.setCreatedAt(LocalDateTime.now());

        // 🔥 Core isolation logic
        if (isAdmin) {
            if ("REPORTED_AGAINST".equals(message.getVisibleTo())) {
                message.setVisibleTo("REPORTED_AGAINST");
            } else {
                message.setVisibleTo("REPORTER");
            }
        } else {
            message.setVisibleTo("ADMIN");
        }

        TicketMessage savedMessage = messageRepository.save(message);

        ticket.setUpdatedAt(LocalDateTime.now());
        ticket.setStatus("IN_PROGRESS");
        ticketRepository.save(ticket);

        // ==================================================
        // NOTIFICATIONS
        // ==================================================

        if (isAdmin) {

            if ("REPORTED_AGAINST".equals(savedMessage.getVisibleTo())) {
                createUserNotification(
                        ticket.getReportedAgainstId(),
                        ticket.getReportedAgainstRole(),
                        "Issue Reported Against You",
                        savedMessage.getMessage(),
                        ticket.getTicketId()
                );
            } else {
                createUserNotification(
                        ticket.getReportedById(),
                        ticket.getReportedByRole(),
                        "Admin Response Received",
                        savedMessage.getMessage(),
                        ticket.getTicketId()
                );
            }

        } else {
            List<User> admins = userRepository.findByRole("ADMIN");
            for (User admin : admins) {
                createUserNotification(
                        admin.getId(),
                        "ADMIN",
                        "User Response Received",
                        "User replied to ticket #" + ticket.getTicketId(),
                        ticket.getTicketId()
                );
            }
        }

        return savedMessage;
    }

    // ======================================================
    // MESSAGE FETCHING
    // ======================================================

    public List<TicketMessage> getTicketMessages(Long ticketId) {
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    public List<TicketMessage> getAdminResponsesForTicket(Long ticketId) {
        return messageRepository.findByTicketIdAndIsAdminResponseTrue(ticketId);
    }

    // ======================================================
    // NOTIFICATIONS
    // ======================================================

    private void createUserNotification(
            String receiverId,
            String receiverRole,
            String title,
            String messageContent,
            String relatedTicketId) {

        if (receiverId == null || receiverId.isEmpty()) return;

        Notification notification = new Notification();
        notification.setUserId(receiverId);
        notification.setUserRole(receiverRole);
        notification.setTitle(title);
        notification.setMessage(messageContent);
        notification.setNotificationType("TICKET_UPDATE");
        notification.setRelatedTicketId(relatedTicketId);
        notification.setRead(false);

        SupportTicket ticket = ticketRepository.findByTicketId(relatedTicketId);
        notification.setEntityId(String.valueOf(ticket != null ? ticket.getId() : null));

        notificationRepository.save(notification);
    }

    public List<Notification> getUserNotifications(String userId, String userRole) {
        return notificationRepository
                .findRelevantNotifications(userId, userRole);
    }


    public long getUnreadNotificationCount(String userId, String userRole) {
        return notificationRepository
                .countUnreadNotifications(userId, userRole);
    }


    @Transactional
    public Notification markNotificationAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllNotificationsAsRead(String userId, String userRole) {
        List<Notification> unread =
                notificationRepository.findUnreadNotifications(userId, userRole);

        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }


    // ======================================================
    // STATS
    // ======================================================

    public Map<String, Object> getSupportStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTickets", ticketRepository.count());
        stats.put("openTickets", ticketRepository.countOpenTickets());
        stats.put("inProgressTickets", ticketRepository.findByStatus("IN_PROGRESS").size());
        stats.put("resolvedTickets", ticketRepository.findByStatus("RESOLVED").size());
        stats.put("closedTickets", ticketRepository.findByStatus("CLOSED").size());
        return stats;
    }

    public List<SupportTicket> getTicketsRelatedToUser(String userId) {
        return ticketRepository.findTicketsRelatedToUser(userId);
    }

    public List<SupportTicket> getTicketsReportedAgainstUser(String userId) {
        return ticketRepository.findByReportedAgainstId(userId);
    }
}
