
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import {
  ApiService,
  RequestDetailDto,
  RequestDto,
  RequestSummary
} from '../core/api.service';
import {
  avatarColor,
  groupClass,
  groupLabel,
  initials,
  kindClass,
  kindLabel,
  money,
  shortDate,
  simpleStatusClass,
  statusLabel
} from '../core/format';
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

  readonly pageSize = 5;
  currentPage = 0;

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
  responseFilter = '';

  selectedId: number | null = null;

  loading = true;
  errorMessage = '';
  lastUpdated: Date | null = null;

  private poller = startPolling(
    () => forkJoin({
      list: this.api.requests(this.status, this.group),
      summary: this.api.requestSummary(),
      detail: this.selectedId !== null
        ? this.api.request(this.selectedId).pipe(
            catchError(err => {
              this.detailError = errorMessage(
                err,
                'Could not load request details.'
              );
              return of(null);
            })
          )
        : of(null)
    }),

    ({ list, summary, detail }) => {
      this.requests = list;
      this.summary = summary;

      list.forEach(r => {
        this.statusOptions.set(r.status, r.statusLabel);
      });

      if (detail) {
        this.detail = detail;
        this.detailError = '';
      }

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

        const g = params.get('group') ?? '';
        const s = params.get('status') ?? '';
        const response = params.get('response') ?? '';

        const idParam = params.get('id');

        const id =
          idParam && /^\d+$/.test(idParam)
            ? Number(idParam)
            : null;

        const changed =
          g !== this.group ||
          s !== this.status ||
          response !== this.responseFilter ||
          id !== this.selectedId;

        if (
          g !== this.group ||
          s !== this.status ||
          response !== this.responseFilter
        ) {
          this.currentPage = 0;
        }

        if (id !== this.selectedId) {
          this.detail = null;
          this.detailError = '';
        }

        this.group = g;
        this.status = s;
        this.responseFilter = response;
        this.selectedId = id;

        if (changed) {
          this.poller.refresh();

          if (id !== null) {
            setTimeout(() => {
              document.getElementById('request-detail')
                ?.scrollIntoView({ behavior: 'smooth' });
            }, 300);
          }
        }
      });
  }

  // =========================================================
  // CLIENT RESPONSE STATUS
  // =========================================================

  getResponseStatus(request: RequestDto): string {

    const responseStatus =
      (request.responseStatus ?? '').trim().toUpperCase();

    // Client acceptance takes priority over closure.
    if (responseStatus === 'ACCEPTED') {
      return 'ACCEPTED';
    }

    // Client rejection takes priority over closure.
    if (responseStatus === 'REJECTED') {
      return 'REJECTED';
    }

    // A quotation closed without a client response.
    if (
      request.closedAt &&
      (request.closureReason ?? '').toUpperCase() === 'NO_RESPONSE'
    ) {
      return 'CLOSED';
    }

    // Read the additional quotation fields returned by the backend.
    const quotation = request as RequestDto & {
      quotationStatus?: string | null;
      quotationSentAt?: string | null;
      quotationPdfAvailable?: boolean;
    };

    const quotationStatus =
      (quotation.quotationStatus ?? '').trim().toUpperCase();

    // A quotation must have been sent successfully,
    // have a sent timestamp, and have a generated PDF.
    const quotationWasSent =
      quotationStatus === 'SENT' &&
      !!quotation.quotationSentAt &&
      quotation.quotationPdfAvailable === true;

    if (!quotationWasSent) {
      return 'NOT_SENT';
    }

    // Sent quotation, waiting for the client's response.
    return 'PENDING';
  }

  // =========================================================
  // RESPONSE COUNTS
  // =========================================================

  get acceptedCount(): number {
    return this.summary?.accepted ?? 0;
  }

  get rejectedCount(): number {
    return this.summary?.rejected ?? 0;
  }

  get pendingCount(): number {
    return this.summary?.pending ?? 0;
  }

  get closedCount(): number {
    return this.summary?.closed ?? 0;
  }

  get totalRequestCount(): number {
    return this.summary?.total ?? this.requests.length;
  }

  // =========================================================
  // RESPONSE FILTER LABEL
  // =========================================================

  get responseLabel(): string {
    switch (this.responseFilter) {
      case 'ACCEPTED':
        return 'Accepted';

      case 'REJECTED':
        return 'Rejected';

      case 'PENDING':
        return 'Pending';

      case 'CLOSED':
        return 'Closed';

      case 'NOT_SENT':
        return 'Not sent';

      default:
        return 'All responses';
    }
  }

  // =========================================================
  // STATUS OPTIONS
  // =========================================================

  get statusList(): { value: string; label: string }[] {
    return [...this.statusOptions.entries()]
      .map(([value, label]) => ({ value, label }));
  }

  // =========================================================
  // FILTERED REQUESTS
  // =========================================================

  get filteredRequests(): RequestDto[] {

    const search = this.searchText.trim().toLowerCase();

    return this.requests.filter(r => {

      const matchesSearch =
        !search ||
        r.reference.toLowerCase().includes(search) ||
        (r.clientName ?? '').toLowerCase().includes(search) ||
        (r.clientCode ?? '').toLowerCase().includes(search) ||
        (r.destination ?? '').toLowerCase().includes(search) ||
        (r.insuranceType ?? '').toLowerCase().includes(search) ||
        (r.clientEmail ?? '').toLowerCase().includes(search);

      const matchesResponse =
        !this.responseFilter ||
        this.getResponseStatus(r) === this.responseFilter;

      return matchesSearch && matchesResponse;
    });
  }

  // =========================================================
  // PAGINATION
  // =========================================================

  get paginatedRequests(): RequestDto[] {

    const start = this.currentPage * this.pageSize;

    return this.filteredRequests.slice(
      start,
      start + this.pageSize
    );
  }

  get totalPages(): number {
    return Math.ceil(
      this.filteredRequests.length / this.pageSize
    );
  }

  get showingFrom(): number {
    return this.filteredRequests.length === 0
      ? 0
      : this.currentPage * this.pageSize + 1;
  }

  get showingTo(): number {
    return Math.min(
      (this.currentPage + 1) * this.pageSize,
      this.filteredRequests.length
    );
  }

  // =========================================================
  // DETAIL FIELD ENTRIES
  // =========================================================

  get fieldEntries(): { key: string; value: string }[] {
    return Object.entries(this.detail?.fields ?? {})
      .map(([key, value]) => ({ key, value }));
  }

  // =========================================================
  // SEARCH AND PAGINATION ACTIONS
  // =========================================================

  onSearchChange(): void {
    this.currentPage = 0;
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
    }
  }

  nextPage(): void {
    if (this.currentPage + 1 < this.totalPages) {
      this.currentPage++;
    }
  }

  private adjustPage(): void {

    const pages = this.totalPages;

    if (pages === 0) {
      this.currentPage = 0;

    } else if (this.currentPage >= pages) {
      this.currentPage = pages - 1;
    }
  }

  // =========================================================
  // URL NAVIGATION
  // =========================================================

  navigateQuery(
    params: Record<string, string | null>
  ): void {

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge'
    });
  }

  applyFilters(): void {

    this.currentPage = 0;

    this.navigateQuery({
      group: this.group || null,
      status: this.status || null
    });
  }

  filterGroup(group: string): void {

    this.group = group;
    this.status = '';
    this.responseFilter = '';

    this.currentPage = 0;

    this.navigateQuery({
      group: group || null,
      status: null,
      response: null
    });
  }

  filterResponse(response: string): void {

    this.responseFilter = response;

    this.group = '';
    this.status = '';

    this.currentPage = 0;

    this.navigateQuery({
      group: null,
      status: null,
      response: response || null
    });
  }

  select(r: RequestDto): void {
    this.navigateQuery({
      id: String(r.id)
    });
  }

  closeDetail(): void {
    this.navigateQuery({
      id: null
    });
  }

  // =========================================================
  // REFRESH AND CLEAR
  // =========================================================

  refresh(): void {
    this.loading = true;
    this.poller.refresh();
  }

  clearFilters(): void {

    this.searchText = '';
    this.currentPage = 0;

    this.navigateQuery({
      group: null,
      status: null,
      response: null
    });
  }

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
