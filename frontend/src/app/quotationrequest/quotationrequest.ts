// import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
// import { CommonModule, Location } from '@angular/common';
// import { FormsModule } from '@angular/forms';
// import { ApiService, RequestDto } from '../core/api.service';
// import { avatarColor, initials, statusLabel } from '../core/format';

// @Component({
//   selector: 'app-quotationrequest',
//   standalone: true,
//   imports: [CommonModule, FormsModule],
//   templateUrl: './quotationrequest.html',
//   styleUrl: './quotationrequest.css'
// })
// export class Quotationrequest implements OnInit {

//   private api = inject(ApiService);
//   private cdr = inject(ChangeDetectorRef);

//   constructor(private location: Location) {}

//   requests: RequestDto[] = [];
//   searchText = '';
//   selectedStatus = 'All Status';
//   loading = true;
//   errorMessage = '';

//   readonly initials = initials;
//   readonly avatarColor = avatarColor;
//   readonly statusLabel = statusLabel;

//   ngOnInit(): void {
//     this.load();
//   }

//   load(): void {
//     this.loading = true;
//     this.errorMessage = '';

//     this.api.requests().subscribe({
//       next: data => {
//         this.requests = data;
//         this.loading = false;
//         this.cdr.markForCheck();
//       },
//       error: () => {
//         this.errorMessage = 'Could not load quotation requests.';
//         this.loading = false;
//         this.cdr.markForCheck();
//       }
//     });
//   }

//   // Group the many backend statuses into the four filter choices.
//   group(status: string): string {
//     if (status === 'QUOTATION_SENT' || status === 'QUOTATION_GENERATED' || status === 'COMPLETED') {
//       return 'Quotation Generated';
//     }
//     if (status === 'MISSING_INFORMATION' || status === 'WAITING_FOR_CLIENT') {
//       return 'Missing Details';
//     }
//     if (status.endsWith('_FAILED') || status === 'MANUAL_REVIEW_REQUIRED') {
//       return 'Needs Attention';
//     }
//     return 'Processing';
//   }

//   statusClass(status: string): string {
//     switch (this.group(status)) {
//       case 'Quotation Generated': return 'generated';
//       case 'Missing Details': return 'missing';
//       case 'Needs Attention': return 'missing';
//       default: return 'processing';
//     }
//   }

//   count(group: string): number {
//     return this.requests.filter(r => this.group(r.status) === group).length;
//   }

//   get filteredRequests(): RequestDto[] {
//     const search = this.searchText.trim().toLowerCase();

//     return this.requests.filter(r => {
//       const matchesSearch =
//         !search ||
//         r.reference.toLowerCase().includes(search) ||
//         (r.clientName ?? '').toLowerCase().includes(search) ||
//         (r.destination ?? '').toLowerCase().includes(search) ||
//         r.clientEmail.toLowerCase().includes(search);

//       const matchesStatus =
//         this.selectedStatus === 'All Status' ||
//         this.group(r.status) === this.selectedStatus;

//       return matchesSearch && matchesStatus;
//     });
//   }

//   refreshRequests(): void {
//     this.searchText = '';
//     this.selectedStatus = 'All Status';
//     this.load();
//   }

//   viewRequest(r: RequestDto): void {
//     alert(
//       `Request: ${r.reference}\n` +
//       `Client: ${r.clientName ?? r.clientEmail}\n` +
//       `Route: ${r.origin ?? '-'} to ${r.destination ?? '-'}\n` +
//       `Dates: ${r.travelStartDate ?? '-'} to ${r.travelEndDate ?? '-'}\n` +
//       `Purpose: ${r.purposeOfTravel ?? '-'}\n` +
//       `Travellers: ${r.travellers ?? '-'}\n` +
//       `Status: ${statusLabel(r.status)}`
//     );
//   }

//   goBack(): void {
//     this.location.back();
//   }
// }




import {
  ChangeDetectorRef,
  Component,
  OnInit,
  inject
} from '@angular/core';

import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService, RequestDto } from '../core/api.service';

import {
  avatarColor,
  initials,
  statusLabel
} from '../core/format';

@Component({
  selector: 'app-quotationrequest',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './quotationrequest.html',
  styleUrl: './quotationrequest.css'
})
export class Quotationrequest implements OnInit {

  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  constructor(private readonly location: Location) {}

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

  /**
   * Load quotation requests from the backend.
   */
  load(): void {
    this.loading = true;
    this.errorMessage = '';

    this.api.requests().subscribe({
      next: (data: RequestDto[]) => {
        this.requests = data ?? [];
        this.loading = false;
        this.cdr.markForCheck();
      },

      error: () => {
        this.requests = [];
        this.errorMessage =
          'Could not load quotation requests. Please try again.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  /**
   * Group backend statuses into the UI status categories.
   */
  group(status: string | null | undefined): string {
    const normalizedStatus = (status ?? '')
      .trim()
      .toUpperCase();

    if (
      [
        'QUOTATION_SENT',
        'QUOTATION_GENERATED',
        'COMPLETED'
      ].includes(normalizedStatus)
    ) {
      return 'Quotation Generated';
    }

    if (
      [
        'MISSING_INFORMATION',
        'WAITING_FOR_CLIENT',
        'CLIENT_REPLY_RECEIVED'
      ].includes(normalizedStatus)
    ) {
      return 'Missing Details';
    }

    if (
      normalizedStatus.endsWith('_FAILED') ||
      normalizedStatus === 'MANUAL_REVIEW_REQUIRED'
    ) {
      return 'Needs Attention';
    }

    return 'Processing';
  }

  /**
   * Return the CSS class for a request status badge.
   */
  statusClass(status: string): string {
    switch (this.group(status)) {
      case 'Quotation Generated':
        return 'generated';

      case 'Missing Details':
      case 'Needs Attention':
        return 'missing';

      default:
        return 'processing';
    }
  }

  /**
   * Count requests belonging to a particular status group.
   */
  count(group: string): number {
    return this.requests.filter(
      request => this.group(request.status) === group
    ).length;
  }

  /**
   * Filter requests by search text and selected status.
   */
  get filteredRequests(): RequestDto[] {
    const search = this.searchText.trim().toLowerCase();

    return this.requests.filter(request => {
      const reference = String(request.reference ?? '').toLowerCase();
      const clientName = String(request.clientName ?? '').toLowerCase();
      const clientEmail = String(request.clientEmail ?? '').toLowerCase();
      const origin = String(request.origin ?? '').toLowerCase();
      const destination = String(request.destination ?? '').toLowerCase();

      const matchesSearch =
        !search ||
        reference.includes(search) ||
        clientName.includes(search) ||
        clientEmail.includes(search) ||
        origin.includes(search) ||
        destination.includes(search);

      const matchesStatus =
        this.selectedStatus === 'All Status' ||
        this.group(request.status) === this.selectedStatus;

      return matchesSearch && matchesStatus;
    });
  }

  /**
   * Reset filters and reload the requests.
   */
  refreshRequests(): void {
    this.searchText = '';
    this.selectedStatus = 'All Status';
    this.load();
  }

  /**
   * Display the selected request details.
   */
  viewRequest(request: RequestDto): void {
    const clientName =
      request.clientName || request.clientEmail || '-';

    alert(
      `Request: ${request.reference || '-'}\n` +
      `Client: ${clientName}\n` +
      `Route: ${request.origin || '-'} to ${request.destination || '-'}\n` +
      `Dates: ${request.travelStartDate || '-'} to ${request.travelEndDate || '-'}\n` +
      `Purpose: ${request.purposeOfTravel || '-'}\n` +
      `Travellers: ${request.travellers ?? '-'}\n` +
      `Status: ${this.statusLabel(request.status)}`
    );
  }

  /**
   * Return to the previous page.
   */
  goBack(): void {
    this.location.back();
  }
}