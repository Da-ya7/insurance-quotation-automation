import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Router } from '@angular/router';
import { goBackOrHome } from '../core/nav';
import { errorMessage } from '../core/http-error';
import { forkJoin } from 'rxjs';
import { ApiService, DashboardStats, ReportSummary } from '../core/api.service';
import { money, shortDate, statusLabel } from '../core/format';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reports.html',
  styleUrl: './reports.css'
})
export class Reports implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  private router = inject(Router);
  private location = inject(Location);

  stats: DashboardStats | null = null;
  summary: ReportSummary | null = null;
  errorMessage = '';

  readonly money = money;
  readonly shortDate = shortDate;
  readonly statusLabel = statusLabel;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.errorMessage = '';

    forkJoin({
      stats: this.api.dashboardStats(),
      summary: this.api.reportSummary()
    }).subscribe({
      next: ({ stats, summary }) => {
        this.stats = stats;
        this.summary = summary;
        this.cdr.markForCheck();
      },
      error: err => {
        this.errorMessage = errorMessage(err, 'Could not load report data.');
        this.cdr.markForCheck();
      }
    });
  }

  // Status breakdown rows with a bar width relative to the biggest group.
  get statusRows(): { label: string; count: number; width: number }[] {
    const entries = Object.entries(this.summary?.requestsByStatus ?? {});
    const max = Math.max(1, ...entries.map(([, count]) => count));

    return entries
      .sort((a, b) => b[1] - a[1])
      .map(([status, count]) => ({
        label: statusLabel(status),
        count,
        width: Math.round((count / max) * 100)
      }));
  }

  get summaryRows(): { label: string; count: number; width: number }[] {
    const s = this.stats;
    if (!s) {
      return [];
    }
    const rows = [
      { label: 'Total Emails', count: s.totalEmails },
      { label: 'Quotation Requests', count: s.quotationRequests },
      { label: 'Quotations Generated', count: s.quotationsGenerated },
      { label: 'Pending Details', count: s.pendingDetails }
    ];
    const max = Math.max(1, ...rows.map(r => r.count));
    return rows.map(r => ({ ...r, width: Math.round((r.count / max) * 100) }));
  }

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
