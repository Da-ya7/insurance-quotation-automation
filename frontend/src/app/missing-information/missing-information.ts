
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { ApiService, MissingDto, MissingSummary } from '../core/api.service';
import { avatarColor, initials, shortDate, statusLabel } from '../core/format';
import { goBackOrHome } from '../core/nav';
import { startPolling } from '../core/polling';

interface MissingGroup {
  requestId: number;
  requestReference: string | null;
  requestStatusLabel: string | null;
  clientCode: string | null;
  clientName: string | null;
  clientEmail: string;
  insuranceType: string | null;
  open: MissingDto[];
  resolved: MissingDto[];
  createdAt: string;
}

@Component({
  selector: 'app-missing-information',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './missing-information.html',
  styleUrl: './missing-information.css'
})
export class MissingInformation {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly shortDate = shortDate;
  readonly statusLabel = statusLabel;

  // PAGINATION SETTINGS
  readonly pageSize = 5;
  currentPage = 0;

  readonly tabs: { value: string; label: string }[] = [
    { value: 'OPEN', label: 'Open' },
    { value: 'RESOLVED', label: 'Resolved' },
    { value: 'ALL', label: 'All' }
  ];

  status = 'OPEN';
  summary: MissingSummary | null = null;
  groups: MissingGroup[] = [];
  itemCount = 0;
  loading = true;
  errorMessage = '';
  lastUpdated: Date | null = null;

  // GET REQUESTS FOR CURRENT PAGE
  get paginatedGroups(): MissingGroup[] {
    const start = this.currentPage * this.pageSize;
    return this.groups.slice(start, start + this.pageSize);
  }

  // TOTAL NUMBER OF PAGES
  get totalPages(): number {
    return Math.max(1, Math.ceil(this.groups.length / this.pageSize));
  }

  // FIRST RECORD NUMBER
  get showingFrom(): number {
    return this.groups.length === 0
      ? 0
      : this.currentPage * this.pageSize + 1;
  }

  // LAST RECORD NUMBER
  get showingTo(): number {
    return Math.min(
      (this.currentPage + 1) * this.pageSize,
      this.groups.length
    );
  }

  private poller = startPolling(
    () => forkJoin({
      list: this.api.missingInformation(this.status),
      summary: this.api.missingSummary()
    }),
    ({ list, summary }) => {
      this.summary = summary;
      this.itemCount = list.length;
      this.groups = this.group(list);

      // Keep the current page valid after refreshing data.
      this.adjustPage();

      this.loading = false;
      this.errorMessage = '';
      this.lastUpdated = new Date();
      this.cdr.markForCheck();
    },
    message => {
      this.errorMessage = message;
      this.loading = false;
      this.cdr.markForCheck();
    }
  );

  constructor() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed())
      .subscribe(params => {

        const raw = (params.get('status') ?? 'OPEN').toUpperCase();

        const s = ['OPEN', 'RESOLVED', 'ALL'].includes(raw)
          ? raw
          : 'OPEN';

        if (s !== this.status) {
          this.status = s;

          // Reset pagination when status changes.
          this.currentPage = 0;

          this.loading = true;
          this.poller.refresh();
        }
      });
  }

  // GROUP MISSING FIELDS BY REQUEST
  private group(items: MissingDto[]): MissingGroup[] {
    const map = new Map<number, MissingGroup>();

    for (const item of items) {
      let g = map.get(item.requestId);

      if (!g) {
        g = {
          requestId: item.requestId,
          requestReference: item.requestReference,
          requestStatusLabel: item.requestStatusLabel,
          clientCode: item.clientCode,
          clientName: item.clientName,
          clientEmail: item.clientEmail,
          insuranceType: item.insuranceType,
          open: [],
          resolved: [],
          createdAt: item.createdAt
        };

        map.set(item.requestId, g);
      }

      (item.status === 'RESOLVED' ? g.resolved : g.open).push(item);

      if (item.createdAt < g.createdAt) {
        g.createdAt = item.createdAt;
      }
    }

    return [...map.values()];
  }

  // LATEST RESOLUTION TIME OF A REQUEST
  resolvedAt(g: MissingGroup): string | null {
    const times = g.resolved
      .map(r => r.resolvedAt)
      .filter((t): t is string => !!t)
      .sort();

    return times.length
      ? times[times.length - 1]
      : null;
  }

  // CHANGE STATUS FILTER
  setStatus(status: string): void {
    this.currentPage = 0;

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status },
      queryParamsHandling: 'merge'
    });
  }

  // PREVIOUS PAGE
  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
    }
  }

  // NEXT PAGE
  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
    }
  }

  // KEEP CURRENT PAGE WITHIN VALID RANGE
  private adjustPage(): void {
    const lastPage = Math.max(0, this.totalPages - 1);

    if (this.currentPage > lastPage) {
      this.currentPage = lastPage;
    }
  }

  // REFRESH DATA
  refresh(): void {
    this.loading = true;
    this.poller.refresh();
  }

  // GO BACK
  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
