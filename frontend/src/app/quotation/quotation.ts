import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, QuotationDto } from '../core/api.service';
import { avatarColor, initials, money, shortDate } from '../core/format';

@Component({
  selector: 'app-quotation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './quotation.html',
  styleUrl: './quotation.css'
})
export class Quotation implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  constructor(private location: Location) {}

  quotations: QuotationDto[] = [];
  searchText = '';
  statusFilter: 'ALL' | 'SENT' | 'NOT_SENT' = 'ALL';
  loading = true;
  errorMessage = '';

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly money = money;
  readonly shortDate = shortDate;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';

    this.api.quotations().subscribe({
      next: data => {
        this.quotations = data;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = 'Could not load quotations.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  get sentCount(): number {
    return this.quotations.filter(q => q.status === 'SENT').length;
  }

  get notSentCount(): number {
    return this.quotations.length - this.sentCount;
  }

  get filteredQuotations(): QuotationDto[] {
    const q = this.searchText.trim().toLowerCase();

    return this.quotations.filter(item => {
      const matchesSearch =
        !q ||
        item.quotationNumber.toLowerCase().includes(q) ||
        (item.clientName ?? '').toLowerCase().includes(q) ||
        item.clientEmail.toLowerCase().includes(q);

      const matchesStatus =
        this.statusFilter === 'ALL' ||
        (this.statusFilter === 'SENT' && item.status === 'SENT') ||
        (this.statusFilter === 'NOT_SENT' && item.status !== 'SENT');

      return matchesSearch && matchesStatus;
    });
  }

  setStatus(filter: 'ALL' | 'SENT' | 'NOT_SENT'): void {
    this.statusFilter = filter;
  }

  clearFilters(): void {
    this.searchText = '';
    this.statusFilter = 'ALL';
  }

  openPdf(q: QuotationDto): void {
    this.api.quotationPdf(q.id).subscribe({
      next: blob => window.open(URL.createObjectURL(blob), '_blank'),
      error: () => alert('Could not open the PDF.')
    });
  }

  downloadPdf(q: QuotationDto): void {
    this.api.quotationPdf(q.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${q.quotationNumber}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => alert('Could not download the PDF.')
    });
  }

  goBack(): void {
    this.location.back();
  }
}
