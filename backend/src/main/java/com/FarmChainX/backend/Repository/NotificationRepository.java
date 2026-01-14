package com.FarmChainX.backend.Repository;

import com.FarmChainX.backend.Model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /* =========================================================
       FETCH ALL RELEVANT (USER + ALL)
    ========================================================= */

    @Query("""
        SELECT n FROM Notification n
        WHERE (n.userId = :userId OR n.userId = 'ALL')
        AND n.userRole = :userRole
        ORDER BY n.createdAt DESC
    """)
    List<Notification> findRelevantNotifications(
            @Param("userId") String userId,
            @Param("userRole") String userRole
    );

    /* =========================================================
       FETCH UNREAD (USER + ALL)
    ========================================================= */

    @Query("""
        SELECT n FROM Notification n
        WHERE (n.userId = :userId OR n.userId = 'ALL')
        AND n.userRole = :userRole
        AND n.isRead = false
        ORDER BY n.createdAt DESC
    """)
    List<Notification> findUnreadNotifications(
            @Param("userId") String userId,
            @Param("userRole") String userRole
    );

    /* =========================================================
       COUNT UNREAD (USER + ALL)
    ========================================================= */

    @Query("""
        SELECT COUNT(n) FROM Notification n
        WHERE (n.userId = :userId OR n.userId = 'ALL')
        AND n.userRole = :userRole
        AND n.isRead = false
    """)
    long countUnreadNotifications(
            @Param("userId") String userId,
            @Param("userRole") String userRole
    );
}
