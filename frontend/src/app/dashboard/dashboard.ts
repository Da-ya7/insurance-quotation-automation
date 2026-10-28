
import {
  ChangeDetectorRef,
  Component,
  OnInit,
  inject
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import {
  ApiService,
  DashboardStats,
  EmailDto,
  RequestDto,
  QuotationDto
} from '../core/api.service';

interface DashboardMetric {
  name: string;
  count: number;
  percentage: number;
}

interface ChartPoint {
  name: string;
  shortLabel: string;
  count: number;
  x: number;
  y: number;
}

type ActivityRange = 'today' | '7days' | '30days';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);

  activeMenu = 'Dashboard';

  // Overall dashboard card totals
  totalEmails = 0;
  quotationRequests = 0;
  pendingDetails = 0;
  quotationsGenerated = 0;
  quotationsSent = 0;

  // Selected graph filter
  selectedRange: ActivityRange = '7days';

  isLoading = false;
  dashboardError = '';

  showProfileMenu = false;
  searchQuery = '';

  // Store records returned by the backend
  private emailRecords: EmailDto[] = [];
  private requestRecords: RequestDto[] = [];
  private quotationRecords: QuotationDto[] = [];

  searchItems = [
    { name: 'Dashboard', route: '/dashboard' },
    { name: 'Incoming Emails', route: '/incoming-emails' },
    { name: 'Quotation Requests', route: '/quotation-requests' },
    { name: 'Missing Information', route: '/missing-information' },
    { name: 'Quotations', route: '/quotations' },
    { name: 'Clients', route: '/clients' },
    { name: 'Reports', route: '/reports' },
    { name: 'Settings', route: '/settings' }
  ];

  emailCategories: DashboardMetric[] = [];

  chartPoints: ChartPoint[] = [];
  chartLinePoints = '';
  chartAxisLabels: number[] = [0, 0, 0, 0, 0];

  get filteredItems() {
    const search = this.searchQuery.toLowerCase().trim();

    if (!search) {
      return [];
    }

    return this.searchItems.filter(item =>
      item.name.toLowerCase().includes(search)
    );
  }

  get selectedRangeLabel(): string {
    switch (this.selectedRange) {
      case 'today':
        return 'Today';

      case '7days':
        return 'Last 7 Days';

      case '30days':
        return 'Last 30 Days';
    }
  }

  ngOnInit(): void {
    this.loadDashboardStats();
    this.loadActivityRecords();
  }

  // Load overall totals for the dashboard cards.
  private loadDashboardStats(): void {
    this.api.dashboardStats().subscribe({
      next: (stats: DashboardStats) => {
        this.totalEmails = Number(stats.totalEmails) || 0;
        this.quotationRequests =
          Number(stats.quotationRequests) || 0;
        this.pendingDetails =
          Number(stats.pendingDetails) || 0;
        this.quotationsGenerated =
          Number(stats.quotationsGenerated) || 0;
        this.quotationsSent =
          Number(stats.quotationsSent) || 0;

        this.updateCategoryBreakdown();

        this.cdr.markForCheck();
      },
      error: error => {
        console.error(
          'Failed to load dashboard statistics:',
          error
        );

        this.dashboardError =
          'Unable to load dashboard statistics. Check the backend connection.';

        this.cdr.markForCheck();
      }
    });
  }

  // Load actual dated records from the existing backend APIs.
  private loadActivityRecords(): void {
    this.isLoading = true;

    forkJoin({
      emails: this.api.emails(),
      requests: this.api.requests(),
      quotations: this.api.quotations()
    }).subscribe({
      next: result => {
        this.emailRecords = result.emails ?? [];
        this.requestRecords = result.requests ?? [];
        this.quotationRecords = result.quotations ?? [];

        this.isLoading = false;
        this.updateActivityChart();
        this.cdr.markForCheck();
      },
      error: error => {
        console.error(
          'Failed to load activity records:',
          error
        );

        this.isLoading = false;
        this.chartPoints = [];
        this.chartLinePoints = '';
        this.chartAxisLabels = [0, 0, 0, 0, 0];

        this.dashboardError =
          'Unable to load activity records. Check the backend APIs.';

        this.cdr.markForCheck();
      }
    });
  }

  // Called by the Today, 7 Days and 30 Days buttons.
  filterActivity(range: ActivityRange): void {
    this.selectedRange = range;

    // Recalculate using the selected date range.
    // No new API request is needed because records are already loaded.
    this.updateActivityChart();

    this.cdr.markForCheck();
  }

  // Determine the start and end dates for the selected filter.
  private getDateRange(): { start: Date; end: Date } {
    const now = new Date();

    const end = new Date(now);
    end.setHours(23, 59, 59, 999);

    const start = new Date(now);
    start.setHours(0, 0, 0, 0);

    if (this.selectedRange === '7days') {
      start.setDate(start.getDate() - 6);
    } else if (this.selectedRange === '30days') {
      start.setDate(start.getDate() - 29);
    }

    return { start, end };
  }

  // Check whether a record date is inside the selected range.
  private isWithinSelectedRange(
    dateValue: string | null | undefined
  ): boolean {
    if (!dateValue) {
      return false;
    }

    const recordDate = new Date(dateValue);

    if (Number.isNaN(recordDate.getTime())) {
      return false;
    }

    const { start, end } = this.getDateRange();

    return recordDate >= start && recordDate <= end;
  }

  // Update the graph using actual records and their dates.
  private updateActivityChart(): void {
    const emailsInRange = this.emailRecords.filter(email =>
      this.isWithinSelectedRange(email.receivedAt)
    ).length;

    const requestsInRange = this.requestRecords.filter(request =>
      this.isWithinSelectedRange(request.createdAt)
    ).length;

    const pendingInRange = this.requestRecords.filter(request =>
      this.isWithinSelectedRange(request.createdAt) &&
      this.isPendingStatus(request.status)
    ).length;

    const quotationsSentInRange =
      this.quotationRecords.filter(quotation =>
        quotation.sentAt != null &&
        this.isWithinSelectedRange(quotation.sentAt)
      ).length;

    const rows = [
      {
        name: 'Incoming Emails',
        count: emailsInRange
      },
      {
        name: 'Quotation Requests',
        count: requestsInRange
      },
      {
        name: 'Pending Requests',
        count: pendingInRange
      },
      {
        name: 'Quotations Sent',
        count: quotationsSentInRange
      }
    ];

    this.updateChartPoints(rows);
  }

  private isPendingStatus(status: string | null | undefined): boolean {
    const normalizedStatus = (status ?? '').toUpperCase();

    return [
      'MISSING_INFORMATION',
      'WAITING_FOR_CLIENT'
    ].includes(normalizedStatus);
  }

  // Keep the category breakdown based on overall backend totals.
  private updateCategoryBreakdown(): void {
    const rows = [
      {
        name: 'Incoming Emails',
        count: this.totalEmails
      },
      {
        name: 'Quotation Requests',
        count: this.quotationRequests
      },
      {
        name: 'Pending Requests',
        count: this.pendingDetails
      },
      {
        name: 'Quotations Sent',
        count: this.quotationsSent
      }
    ];

    const maxCount = Math.max(
      1,
      ...rows.map(row => row.count)
    );

    this.emailCategories = rows.map(row => ({
      ...row,
      percentage: Math.round(
        (row.count / maxCount) * 100
      )
    }));
  }

  // Draw the chart using the counts for the selected range.
  private updateChartPoints(
    rows: { name: string; count: number }[]
  ): void {
    const xPositions = [130, 350, 570, 790];

    const shortLabels = [
      'Emails',
      'Requests',
      'Pending',
      'Sent'
    ];

    const topY = 65;
    const bottomY = 280;

    const maxCount = Math.max(
      1,
      ...rows.map(row => row.count)
    );

    this.chartAxisLabels = [0, 1, 2, 3, 4].map(step =>
      Math.round(maxCount * (1 - step / 4))
    );

    this.chartPoints = rows.map((row, index) => {
      const count = Math.max(0, row.count);

      const y =
        bottomY -
        (count / maxCount) * (bottomY - topY);

      return {
        name: row.name,
        shortLabel: shortLabels[index],
        count,
        x: xPositions[index],
        y: Math.max(topY, Math.min(bottomY, y))
      };
    });

    this.chartLinePoints = this.chartPoints
      .map(point => `${point.x},${point.y}`)
      .join(' ');
  }

  goToPage(route: string): void {
    this.showProfileMenu = false;
    this.router.navigate([route]);
  }

  toggleProfileMenu(): void {
    this.showProfileMenu = !this.showProfileMenu;
  }

  logout(): void {
    const confirmed = window.confirm(
      'Are you sure you want to logout?'
    );

    if (confirmed) {
      localStorage.removeItem('token');
      localStorage.removeItem('authToken');
      localStorage.removeItem('isLoggedIn');

      this.showProfileMenu = false;
      this.router.navigate(['/login']);
    }
  }
}
