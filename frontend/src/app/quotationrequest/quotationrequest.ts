import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, RequestDto } from '../core/api.service';
import { avatarColor, initials, statusLabel } from '../core/format';

@Component({
  selector: 'app-quotationrequest',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './quotationrequest.html',
  styleUrl: './quotationrequest.css'
})
export class Quotationrequest implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  constructor(private location: Location) {}

  requests: RequestDto[] = [];
  searchText = '';
  selectedStatus = 'All Status';
  loading = true;
  errorMessage = '';

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly statusLabel = statusLabel;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';

    this.api.requests().subscribe({
      next: data => {
        this.requests = data;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = 'Could not load quotation requests.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  // Group the many backend statuses into the four filter choices.
  group(status: string): string {
    if (status === 'QUOTATION_SENT' || status === 'QUOTATION_GENERATED' || status === 'COMPLETED') {
      return 'Quotation Generated';
    }
    if (status === 'MISSING_INFORMATION' || status === 'WAITING_FOR_CLIENT') {
      return 'Missing Details';
    }
    if (status.endsWith('_FAILED') || status === 'MANUAL_REVIEW_REQUIRED') {
      return 'Needs Attention';
    }
    return 'Processing';
  }

  statusClass(status: string): string {
    switch (this.group(status)) {
      case 'Quotation Generated': return 'generated';
      case 'Missing Details': return 'missing';
      case 'Needs Attention': return 'missing';
      default: return 'processing';
    }
  }

  count(group: string): number {
    return this.requests.filter(r => this.group(r.status) === group).length;
  }

  get filteredRequests(): RequestDto[] {
    const search = this.searchText.trim().toLowerCase();

    return this.requests.filter(r => {
      const matchesSearch =
        !search ||
        r.reference.toLowerCase().includes(search) ||
        (r.clientName ?? '').toLowerCase().includes(search) ||
        (r.destination ?? '').toLowerCase().includes(search) ||
        r.clientEmail.toLowerCase().includes(search);

      const matchesStatus =
        this.selectedStatus === 'All Status' ||
        this.group(r.status) === this.selectedStatus;

      return matchesSearch && matchesStatus;
    });
  }

  refreshRequests(): void {
    this.searchText = '';
    this.selectedStatus = 'All Status';
    this.load();
  }

  viewRequest(r: RequestDto): void {
    alert(
      `Request: ${r.reference}\n` +
      `Client: ${r.clientName ?? r.clientEmail}\n` +
      `Route: ${r.origin ?? '-'} to ${r.destination ?? '-'}\n` +
      `Dates: ${r.travelStartDate ?? '-'} to ${r.travelEndDate ?? '-'}\n` +
      `Purpose: ${r.purposeOfTravel ?? '-'}\n` +
      `Travellers: ${r.travellers ?? '-'}\n` +
      `Status: ${statusLabel(r.status)}`
    );
  }

  goBack(): void {
    this.location.back();
  }
}
