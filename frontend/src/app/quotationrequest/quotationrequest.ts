import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { ApiService, RequestDetailDto, RequestDto, RequestSummary } from '../core/api.service';
import { avatarColor, groupClass, groupLabel, initials, kindClass, kindLabel, money, shortDate, simpleStatusClass, statusLabel } from '../core/format';
import { errorMessage } from '../core/http-error';
import { goBackOrHome } from '../core/nav';
import { startPolling } from '../core/polling';

@Component({
  selector: 'app-quotationrequest',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './quotationrequest.html',
  styleUrl: './quotationrequest.css'
})
export class Quotationrequest {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly statusLabel = statusLabel;
  readonly groupClass = groupClass;
  readonly groupLabel = groupLabel;
  readonly kindClass = kindClass;
  readonly kindLabel = kindLabel;
  readonly statusClass = simpleStatusClass;
  readonly money = money;
  readonly shortDate = shortDate;

  readonly groups = [
    { value: '', label: 'All groups' },
    { value: 'PROCESSING', label: 'Processing' },
    { value: 'MISSING_DETAILS', label: 'Missing Details' },
    { value: 'COMPLETED', label: 'Quotation Sent' },
    { value: 'NEEDS_ATTENTION', label: 'Needs Attention' }
  ];

  requests: RequestDto[] = [];
  summary: RequestSummary | null = null;
  statusOptions = new Map<string, string>();
  detail: RequestDetailDto | null = null;
  detailError = '';

  searchText = '';
  group = '';
  status = '';
  selectedId: number | null = null;

  loading = true;
  errorMessage = '';
  lastUpdated: Date | null = null;

  private poller = startPolling(
    () => forkJoin({
      list: this.api.requests(this.status, this.group),
      summary: this.api.requestSummary(),
      detail: this.selectedId !== null
        ? this.api.request(this.selectedId).pipe(catchError(err => {
            this.detailError = errorMessage(err, 'Could not load request details.');
            return of(null);
          }))
        : of(null)
    }),
    ({ list, summary, detail }) => {
      this.requests = list;
      this.summary = summary;
      list.forEach(r => this.statusOptions.set(r.status, r.statusLabel));
      if (detail) {
        this.detail = detail;
        this.detailError = '';
      }
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
      const g = params.get('group') ?? '';
      const s = params.get('status') ?? '';
      const idParam = params.get('id');
      const id = idParam && /^\d+$/.test(idParam) ? Number(idParam) : null;
      const changed = g !== this.group || s !== this.status || id !== this.selectedId;
      if (id !== this.selectedId) {
        this.detail = null;
        this.detailError = '';
      }
      this.group = g;
      this.status = s;
      this.selectedId = id;
      if (changed) {
        this.poller.refresh();
        if (id !== null) {
          setTimeout(() => document.getElementById('request-detail')?.scrollIntoView({ behavior: 'smooth' }), 300);
        }
      }
    });
  }

  get statusList(): { value: string; label: string }[] {
    return [...this.statusOptions.entries()].map(([value, label]) => ({ value, label }));
  }

  get filteredRequests(): RequestDto[] {
    const search = this.searchText.trim().toLowerCase();
    return this.requests.filter(r =>
      !search ||
      r.reference.toLowerCase().includes(search) ||
      (r.clientName ?? '').toLowerCase().includes(search) ||
      (r.clientCode ?? '').toLowerCase().includes(search) ||
      (r.destination ?? '').toLowerCase().includes(search) ||
      (r.insuranceType ?? '').toLowerCase().includes(search) ||
      r.clientEmail.toLowerCase().includes(search));
  }

  get fieldEntries(): { key: string; value: string }[] {
    return Object.entries(this.detail?.fields ?? {}).map(([key, value]) => ({ key, value }));
  }

  navigateQuery(params: Record<string, string | null>): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: params, queryParamsHandling: 'merge' });
  }

  applyFilters(): void {
    this.navigateQuery({ group: this.group || null, status: this.status || null });
  }

  filterGroup(group: string): void {
    this.group = group;
    this.status = '';
    this.applyFilters();
  }

  select(r: RequestDto): void {
    this.navigateQuery({ id: String(r.id) });
  }

  closeDetail(): void {
    this.navigateQuery({ id: null });
  }

  refresh(): void {
    this.loading = true;
    this.poller.refresh();
  }

  clearFilters(): void {
    this.searchText = '';
    this.navigateQuery({ group: null, status: null });
  }

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
