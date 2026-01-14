import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';
import { BatchCardComponent } from '../batch-card/batch-card.component';

@Component({
  selector: 'app-distributor-dashboard',
  standalone: true,
  imports: [CommonModule, BatchCardComponent],
  templateUrl: './distributor-dashboard.component.html'
})
export class DistributorDashboardComponent implements OnInit {

  distributorId!: number;

  tab: 'BATCHES' | 'ORDERS' | 'HISTORY' = 'BATCHES';
  tabs: Array<'BATCHES' | 'ORDERS' | 'HISTORY'> = ['BATCHES', 'ORDERS', 'HISTORY'];

  pendingBatches: any[] = [];
  approvedBatches: any[] = [];
  orders: any[] = [];

  private API = 'http://localhost:8080/api';

  constructor(
    private http: HttpClient,
    private auth: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const user = this.auth.userValue;
    if (!user) return;

    this.distributorId = user.id || user.distributorId;

    this.fetchBatches();
    this.fetchOrders();
  }

  /* ---------------- BATCHES ---------------- */

  fetchBatches(): void {
    this.http.get<any[]>(`${this.API}/batches/pending`)
      .subscribe(res => this.pendingBatches = (res || []).filter(b => b != null));

    this.http.get<any[]>(`${this.API}/batches/approved/${this.distributorId}`)
      .subscribe(res => this.approvedBatches = (res || []).filter(b => b != null));
  }

  approveBatch(batchId: string): void {
    this.http
      .put(`${this.API}/batches/distributor/approve/${batchId}/${this.distributorId}`, {})
      .subscribe(() => this.fetchBatches());
  }

  rejectBatch(batchId: string): void {
    const reason = prompt('Reason for rejection?');
    if (!reason) return;

    this.http
      .put(`${this.API}/batches/distributor/reject/${batchId}/${this.distributorId}`, { reason })
      .subscribe(() => this.fetchBatches());
  }

  goToTrace(batchId: string): void {
    window.open(`http://localhost:4200/trace/${batchId}`, '_blank');
  }

  /* ---------------- ORDERS ---------------- */

  fetchOrders(): void {
    this.http
      .get<any[]>(`${this.API}/orders/distributor/${this.distributorId}`)
      .subscribe({
        next: res => this.orders = (res || []).filter(o => o != null && o.status),
        error: () => this.orders = []
      });
  }

  updateStatus(order: any, status: string): void {
    let location: string | null = null;

    if (status === 'IN_WAREHOUSE') {
      location = prompt('Enter warehouse location:');
      if (!location) return;
    } else if (status === 'IN_TRANSIT') {
      location = prompt('Enter transit location:');
      if (!location) return;
    }

    this.http.put(`${this.API}/orders/${order.orderId}/status`, null, {
      params: {
        status,
        distributorId: this.distributorId,
        location: location || ''
      }
    }).subscribe(() => this.fetchOrders());
  }

  cancelOrder(orderId: number): void {
    const reason = prompt('Reason for cancellation?');
    if (!reason) return;

    this.http.put(`${this.API}/orders/${orderId}/cancel`, null, {
      params: { distributorId: this.distributorId, reason }
    }).subscribe(() => this.fetchOrders());
  }

  /* ---------------- FILTERS ---------------- */

  onTabChange(t: 'BATCHES' | 'ORDERS' | 'HISTORY') {
    this.tab = t;
    if (t === 'ORDERS' || t === 'HISTORY') {
      this.fetchOrders();
    }
  }

  /* ---------------- COMPUTED ---------------- */

  get liveOrders() {
    return this.orders.filter(o => o?.status && o.status !== 'DELIVERED');
  }

  get historyOrders() {
    return this.orders.filter(o => o?.status === 'DELIVERED');
  }

  get totalDistributorEarnings() {
    return this.historyOrders.reduce(
      (sum, o) => sum + (o?.totalAmount || 0),
      0
    );
  }

  getDistributorProfit(order: any) {
    return Math.round((order?.totalAmount || 0) * 0.1);
  }
}
