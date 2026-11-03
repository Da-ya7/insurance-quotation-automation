
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import {
  ApiService, QuotationDetailDto, QuotationDto, QuotationHistoryDto, QuotationSummary
} from '../core/api.service';
import { avatarColor, initials, money, shortDate, simpleStatusClass, statusLabel } from '../core/format';
import { errorMessage } from '../core/http-error';
import { goBackOrHome } from '../core/nav';
import { startPolling } from '../core/polling';

@Component({
  selector: 'app-quotation',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './quotation.html',
  styleUrl: './quotation.css'
})
export class Quotation {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly money = money;
  readonly shortDate = shortDate;
  readonly statusClass = simpleStatusClass;
  readonly statusLabel = statusLabel;

  // Pagination: display 5 quotations per page.
  readonly pageSize = 5;
  currentPage = 0;

  quotations: QuotationDto[] = [];
  summary: QuotationSummary | null = null;
  detail: QuotationDetailDto | null = null;
  history: QuotationHistoryDto[] = [];
  detailError = '';

  searchText = '';
  statusFilter = ''; // '' | SENT | DRAFT | SUPERSEDED
  selectedId: number | null = null;
  loading = true;
  errorMessage = '';
  actionError = '';
  lastUpdated: Date | null = null;

  private poller = startPolling(
    () => forkJoin({
      list: this.api.quotations(this.statusFilter),
      summary: this.api.quotationSummary(),
      detail: this.selectedId !== null
        ? this.api.quotation(this.selectedId).pipe(catchError(err => {
            this.detailError = errorMessage(err, 'Could not load quotation details.');
            return of(null);
          }))
        : of(null),
      history: this.selectedId !== null
        ? this.api.quotationHistory(this.selectedId).pipe(catchError(() => of(null)))
        : of(null)
    }),
    ({ list, summary, detail, history }) => {
      this.quotations = list;
      this.summary = summary;

      this.adjustPage();

      if (detail) {
        this.detail = detail;
        this.detailError = '';
      }

      if (history) {
        this.history = history;
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
      const raw = (params.get('status') ?? '').toUpperCase();
      const s = ['SENT', 'DRAFT', 'SUPERSEDED'].includes(raw) ? raw : '';

      const idParam = params.get('id');
      const id = idParam && /^\d+$/.test(idParam) ? Number(idParam) : null;

      const statusChanged = s !== this.statusFilter;
      const changed = statusChanged || id !== this.selectedId;

      if (statusChanged) {
        this.currentPage = 0;
      }

      if (id !== this.selectedId) {
        this.detail = null;
        this.history = [];
        this.detailError = '';
      }

      this.statusFilter = s;
      this.selectedId = id;

      if (changed) {
        this.poller.refresh();
      }
    });
  }

  get filteredQuotations(): QuotationDto[] {
    const q = this.searchText.trim().toLowerCase();

    return this.quotations.filter(item =>
      !q ||
      item.quotationNumber.toLowerCase().includes(q) ||
      (item.clientName ?? '').toLowerCase().includes(q) ||
      (item.clientCode ?? '').toLowerCase().includes(q) ||
      item.clientEmail.toLowerCase().includes(q)
    );
  }

  // Return only the five quotations for the current page.
  get paginatedQuotations(): QuotationDto[] {
    const start = this.currentPage * this.pageSize;
    return this.filteredQuotations.slice(start, start + this.pageSize);
  }

  // Total number of pages.
  get totalPages(): number {
    return Math.ceil(this.filteredQuotations.length / this.pageSize);
  }

  // First record number displayed.
  get showingFrom(): number {
    return this.filteredQuotations.length === 0
      ? 0
      : this.currentPage * this.pageSize + 1;
  }

  // Last record number displayed.
  get showingTo(): number {
    return Math.min(
      (this.currentPage + 1) * this.pageSize,
      this.filteredQuotations.length
    );
  }

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
    this.currentPage = Math.min(
      this.currentPage,
      Math.max(0, this.totalPages - 1)
    );
  }

  setStatus(filter: string): void {
    this.currentPage = 0;
    this.navigateQuery({ status: filter || null });
  }

  select(q: QuotationDto): void {
    this.navigateQuery({ id: String(q.id) });
  }

  closeDetail(): void {
    this.navigateQuery({ id: null });
  }

  private navigateQuery(params: Record<string, string | null>): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.searchText = '';
    this.currentPage = 0;
    this.navigateQuery({ status: null });
  }

  refresh(): void {
    this.loading = true;
    this.poller.refresh();
  }

  // The PDF endpoint needs the Authorization header, so it is fetched as a blob.
  openPdf(q: { id: number }): void {
    this.actionError = '';

    this.api.quotationPdf(q.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: err => {
        this.actionError = errorMessage(err, 'Could not open the PDF.');
        this.cdr.markForCheck();
      }
    });
  }

  downloadPdf(q: { id: number; quotationNumber: string }): void {
    this.actionError = '';

    this.api.quotationPdf(q.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${q.quotationNumber}.pdf`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
      },
      error: err => {
        this.actionError = errorMessage(err, 'Could not download the PDF.');
        this.cdr.markForCheck();
      }
    });
  }

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
