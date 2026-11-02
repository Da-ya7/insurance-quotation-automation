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

  private poller = startPolling(
    () => forkJoin({
      list: this.api.missingInformation(this.status),
      summary: this.api.missingSummary()
    }),
    ({ list, summary }) => {
      this.summary = summary;
      this.itemCount = list.length;
      this.groups = this.group(list);
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
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(params => {
      const raw = (params.get('status') ?? 'OPEN').toUpperCase();
      const s = ['OPEN', 'RESOLVED', 'ALL'].includes(raw) ? raw : 'OPEN';
      if (s !== this.status) {
        this.status = s;
        this.loading = true;
        this.poller.refresh();
      }
    });
  }

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

  // Latest resolution time of a request's resolved items.
  resolvedAt(g: MissingGroup): string | null {
    const times = g.resolved.map(r => r.resolvedAt).filter((t): t is string => !!t).sort();
    return times.length ? times[times.length - 1] : null;
  }

  setStatus(status: string): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { status }, queryParamsHandling: 'merge' });
  }

  refresh(): void {
    this.loading = true;
    this.poller.refresh();
  }

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
