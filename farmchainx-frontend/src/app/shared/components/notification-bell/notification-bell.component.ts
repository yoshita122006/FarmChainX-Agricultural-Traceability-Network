import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

import { NotificationService } from '../../../core/services/notification.service';
import { AuthService } from '../../../core/services/auth.service';

interface AppNotification {
  id: number;
  userId: string;
  userRole: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;

  notificationType:
    | 'TICKET_CREATED'
    | 'TICKET_UPDATE'
    | 'NEW_BATCH'
    | 'NEW_ORDER'
    | 'ORDER_STATUS_UPDATED'
    | 'BATCH_APPROVED'
    | 'DELIVERY_COMPLETED';

  entityId?: number;
  relatedTicketId?: string;
}

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-bell.component.html'
})
export class NotificationBellComponent implements OnInit {

  notifications: AppNotification[] = [];
  unreadCount = 0;
  showDropdown = false;
  loading = false;

  user!: { id: string; role: string };

  constructor(
    private notificationService: NotificationService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.authService.user$.subscribe((u: any) => {
      if (!u) return;
      this.user = { id: u.id, role: u.role };
      this.loadNotifications();
    });
  }

  loadNotifications(): void {
    this.loading = true;

    this.notificationService
      .getUserNotifications(this.user.id, this.user.role)
      .subscribe({
        next: (res: any) => {
          // Supports both List<> and { notifications: [] }
          this.notifications = Array.isArray(res)
            ? res
            : res?.notifications ?? [];

          this.unreadCount =
            this.notifications.filter(n => !n.read).length;

          this.loading = false;
        },
        error: () => {
          this.notifications = [];
          this.unreadCount = 0;
          this.loading = false;
        }
      });
  }

  toggleDropdown(): void {
    this.showDropdown = !this.showDropdown;
  }

  close(): void {
    this.showDropdown = false;
  }

  markAsRead(n: AppNotification, e: Event): void {
    e.stopPropagation();

    if (n.read) return;

    this.notificationService.markAsRead(n.id).subscribe(() => {
      n.read = true;
      this.unreadCount--;
    });
  }

  markAllAsRead(): void {
    this.notificationService
      .markAllAsRead(this.user.id, this.user.role)
      .subscribe(() => {
        this.notifications.forEach(n => (n.read = true));
        this.unreadCount = 0;
      });
  }

  delete(n: AppNotification, e: Event): void {
    e.stopPropagation();

    this.notificationService.deleteNotification(n.id).subscribe(() => {
      this.notifications =
        this.notifications.filter(x => x.id !== n.id);

      this.unreadCount =
        this.notifications.filter(x => !x.read).length;
    });
  }

  /* ============================
     CLICK ACTION BASED ON TYPE
  ============================ */
  handleNotificationClick(n: AppNotification): void {

    if (!n.read) {
      this.notificationService.markAsRead(n.id).subscribe();
      n.read = true;
      this.unreadCount--;
    }

    switch (n.notificationType) {

      case 'TICKET_CREATED':
      case 'TICKET_UPDATE':
        this.router.navigate(['/support/tickets', n.entityId]);
        break;

      case 'NEW_BATCH':
        this.router.navigate(['/distributor/batches']);
        break;

      case 'NEW_ORDER':
        this.router.navigate(['/distributor/orders']);
        break;

      case 'ORDER_STATUS_UPDATED':
        this.router.navigate(['/consumer/orders', n.entityId]);
        break;

      case 'BATCH_APPROVED':
        this.router.navigate(['/farmer/batches', n.entityId]);
        break;

      case 'DELIVERY_COMPLETED':
        this.router.navigate(['/orders/history']);
        break;
    }

    this.close();
  }

  /* ============================
     ICON MAPPING
  ============================ */
  getIcon(type: AppNotification['notificationType']): string {
    switch (type) {
      case 'TICKET_CREATED': return '🎫';
      case 'TICKET_UPDATE': return '💬';
      case 'NEW_BATCH': return '🌾';
      case 'NEW_ORDER': return '🛒';
      case 'ORDER_STATUS_UPDATED': return '📦';
      case 'BATCH_APPROVED': return '✅';
      case 'DELIVERY_COMPLETED': return '🚚';
      default: return '🔔';
    }
  }
}
