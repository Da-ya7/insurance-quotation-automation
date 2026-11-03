import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, DashboardStats } from '../core/api.service';
import { clearSession, currentRole, currentUserName } from '../core/auth';
import { GROUP_COLORS, groupLabel, shortDate } from '../core/format';
import { startPolling } from '../core/polling';

interface StatCard {
  title: string;
  icon: string;
  value: number;
  note: string;
  warn?: boolean;
  link: string;
  query?: Record<string, string>;
}

interface ChartPoint {
  x: number;
  y: number;
  label: string;
  requests: number;
  quotations: number;
}

interface GroupRow {
  group: string;
  label: string;
  count: number;
  percentage: number;
  color: string;
  link: Record<string, string>;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);

  readonly userName = currentUserName() || 'User';
  readonly role = currentRole();

  stats: DashboardStats | null = null;
  cards: StatCard[] = [];
  groupRows: GroupRow[] = [];
  requestPoints: ChartPoint[] = [];
  quotationPoints: ChartPoint[] = [];
  requestLine = '';
  quotationLine = '';
  yTicks: { y: number; label: number }[] = [];
  xLabels: { x: number; label: string }[] = [];
  hasChartData = false;
  hasGroupData = false;

  loading = true;
  errorMessage = '';

  readonly W = 900;
  readonly H = 350;
  private readonly left = 60;
  private readonly right = 850;
  private readonly top = 40;
  private readonly bottom = 290;

  readonly shortDate = shortDate;

  showProfileMenu = false;
  searchQuery = '';

  searchItems = [
    { name: 'Dashboard', route: '/dashboard' },
    { name: 'Incoming Emails', route: '/incoming-emails' },
    { name: 'Quotation Requests', route: '/quotation-requests' },
    { name: 'Missing Information', route: '/missing-information' },
    { name: 'Quotations', route: '/quotations' },
    { name: 'Clients', route: '/clients' },
    { name: 'Master Data', route: '/master-data' },
    { name: 'Reports', route: '/reports' },
    { name: 'Settings', route: '/settings' }
  ];

  get filteredItems() {
    const search = this.searchQuery.toLowerCase().trim();
    return search
      ? this.searchItems.filter(i => i.name.toLowerCase().includes(search))
      : [];
  }

  // Automatic refresh every 30 seconds.
  private poller = startPolling(
    () => this.api.dashboardStats(),
    stats => {
      this.apply(stats);
      this.loading = false;
      this.errorMessage = '';
      this.cdr.markForCheck();
    },
    message => {
      this.errorMessage = message;
      this.loading = false;
      this.cdr.markForCheck();
    }
  );

  refresh(): void {
    this.poller.refresh();
  }

  private apply(s: DashboardStats): void {
    this.stats = s;

    // Only four dashboard cards
    this.cards = [
      {
        title: 'Incoming Emails',
        icon: '📧',
        value: s.totalEmails,
        note: `${s.newRequestEmails} new requests · ${s.replyEmails} replies`,
        link: '/incoming-emails'
      },
      {
        title: 'Quotation Requests',
        icon: '📋',
        value: s.quotationRequests,
        note: `${s.requestsProcessing} processing`,
        link: '/quotation-requests'
      },
      {
        title: 'Missing Details',
        icon: '⚠️',
        value: s.requestsMissing,
        note: `${s.openMissingFields} open field(s)`,
        warn: s.requestsMissing > 0,
        link: '/missing-information',
        query: { status: 'OPEN' }
      },
      {
        title: 'Quotations Sent',
        icon: '📄',
        value: s.quotationsSent,
        note: `${s.quotationsNotSent} not sent yet`,
        link: '/quotations',
        query: { status: 'SENT' }
      }
    ];

    this.buildChart(s);
    this.buildGroups(s);
  }

  private buildGroups(s: DashboardStats): void {
    const rows = s.requestsByGroup ?? [];
    const total = rows.reduce((sum, r) => sum + r.count, 0);

    this.hasGroupData = total > 0;

    this.groupRows = rows.map(r => ({
      group: r.group,
      label: r.label || groupLabel(r.group),
      count: r.count,
      percentage: total ? Math.round((r.count / total) * 100) : 0,
      color: GROUP_COLORS[r.group] ?? '#6b7280',
      link: { group: r.group }
    }));
  }

  private buildChart(s: DashboardStats): void {
    const days = s.dailyRequests ?? [];
    const rawMax = Math.max(
      0,
      ...days.map(d => Math.max(d.requests, d.quotations))
    );

    this.hasChartData = rawMax > 0;

    const max = this.niceMax(rawMax);
    const n = days.length;

    const stepX = n > 1
      ? (this.right - this.left) / (n - 1)
      : 0;

    const yOf = (v: number) =>
      this.bottom - (v / max) * (this.bottom - this.top);

    const xOf = (i: number) =>
      n > 1
        ? this.left + i * stepX
        : (this.left + this.right) / 2;

    const pts = (key: 'requests' | 'quotations'): ChartPoint[] =>
      days.map((d, i) => ({
        x: xOf(i),
        y: yOf(d[key]),
        label: d.date,
        requests: d.requests,
        quotations: d.quotations
      }));

    this.requestPoints = pts('requests');
    this.quotationPoints = pts('quotations');

    this.requestLine = this.requestPoints
      .map(p => `${p.x},${p.y}`)
      .join(' ');

    this.quotationLine = this.quotationPoints
      .map(p => `${p.x},${p.y}`)
      .join(' ');

    this.yTicks = [0, 1, 2, 3, 4].map(i => ({
      y: yOf((max / 4) * i),
      label: Math.round((max / 4) * i * 100) / 100
    }));

    this.xLabels = days
      .map((d, i) => ({
        x: xOf(i),
        label: d.date.slice(5)
      }))
      .filter((_, i) => n <= 7 || i % 2 === 0 || i === n - 1);
  }

  private niceMax(v: number): number {
    if (v <= 4) {
      return 4;
    }

    return Math.ceil(v / 4) * 4;
  }

  goToPage(route: string): void {
    this.router.navigate([route]);
    this.searchQuery = '';
  }

  toggleProfileMenu(): void {
    this.showProfileMenu = !this.showProfileMenu;
  }

  logout(): void {
    if (window.confirm('Are you sure you want to logout?')) {
      clearSession();
      this.showProfileMenu = false;
      this.router.navigate(['/login']);
    }
  }
}
