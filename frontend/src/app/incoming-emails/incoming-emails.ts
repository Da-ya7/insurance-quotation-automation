import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { ApiService, EmailDto, EmailSummary } from '../core/api.service';
import { avatarColor, initials, kindClass, kindLabel, shortDate, simpleStatusClass, statusLabel } from '../core/format';
import { goBackOrHome } from '../core/nav';
import { startPolling } from '../core/polling';

@Component({
  selector: 'app-incoming-emails',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './incoming-emails.html',
  styleUrl: './incoming-emails.css'
})
export class IncomingEmails {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly shortDate = shortDate;
  readonly kindLabel = kindLabel;
  readonly kindClass = kindClass;
  readonly statusClass = simpleStatusClass;
  readonly statusLabel = statusLabel;

  readonly kinds = [
    { value: '', label: 'All kinds' },
    { value: 'NEW_REQUEST', label: 'New request' },
    { value: 'CLIENT_REPLY', label: 'Client reply' },
    { value: 'UNRELATED', label: 'Unrelated' }
  ];
  readonly statuses = [
    { value: '', label: 'All statuses' },
    { value: 'RECEIVED', label: 'Pending' },
    { value: 'PROCESSED', label: 'Processed' },
    { value: 'FAILED', label: 'Failed' },
    { value: 'IGNORED', label: 'Ignored' }
  ];

  emails: EmailDto[] = [];
  summary: EmailSummary | null = null;
  searchText = '';
  kind = '';
  status = '';
  selectedId: number | null = null;
  loading = true;
  errorMessage = '';
  lastUpdated: Date | null = null;

  private poller = startPolling(
    () => forkJoin({
      list: this.api.emails(this.kind, this.status),
      summary: this.api.emailSummary()
    }),
    ({ list, summary }) => {
      this.emails = list;
      this.summary = summary;
      if (this.selectedId === null && list.length > 0) {
        this.selectedId = list[0].id;
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
      const k = params.get('kind') ?? '';
      const s = params.get('status') ?? '';
      const changed = k !== this.kind || s !== this.status;
      this.kind = k;
      this.status = s;
      if (changed) {
        this.loading = true;
        this.poller.refresh();
      }
    });
  }

  get filteredEmails(): EmailDto[] {
    const q = this.searchText.trim().toLowerCase();
    return this.emails.filter(e =>
      !q ||
      e.senderEmail.toLowerCase().includes(q) ||
      (e.subject ?? '').toLowerCase().includes(q) ||
      (e.clientName ?? '').toLowerCase().includes(q) ||
      (e.clientCode ?? '').toLowerCase().includes(q));
  }

  get selectedEmail(): EmailDto | undefined {
    return this.emails.find(e => e.id === this.selectedId);
  }

  selectEmail(email: EmailDto): void {
    this.selectedId = email.id;
  }

  // Filters live in the URL so links such as ?kind=NEW_REQUEST&status=FAILED work.
  applyFilters(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { kind: this.kind || null, status: this.status || null },
      queryParamsHandling: 'merge'
    });
  }

  quickFilter(kind: string, status: string): void {
    this.kind = kind;
    this.status = status;
    this.applyFilters();
  }

  clearFilters(): void {
    this.searchText = '';
    this.quickFilter('', '');
  }

  refresh(): void {
    this.loading = true;
    this.poller.refresh();
  }

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
