
import {
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';

import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, EmailDto } from '../core/api.service';
import {
  avatarColor,
  initials,
  shortDate
} from '../core/format';

interface IncomingEmail {
  id: number;
  sender: string;
  email: string;
  subject: string;
  category: string;
  status: string;
  time: string;
  initials: string;
  color: string;
  body: string[];
  name: string;
  detectedEmail: string;
}

@Component({
  selector: 'app-incoming-emails',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './incoming-emails.html',
  styleUrl: './incoming-emails.css'
})
export class IncomingEmails implements OnInit, OnDestroy {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  constructor(private location: Location) {}

  searchText = '';
  activeFilter = 'All';

  // Keep this property for compatibility with the existing HTML.
  selectedEmailId = 1;

  // Use the database email ID to identify the selected email.
  private selectedEmailKey: number | null = null;

  emails: IncomingEmail[] = [];

  private timer: ReturnType<typeof setInterval> | undefined;

  ngOnInit(): void {
    this.loadEmails();

    // Refresh incoming emails every 30 seconds.
    this.timer = setInterval(() => {
      this.loadEmails();
    }, 30000);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  loadEmails(): void {
    this.api.emails().subscribe({
      next: (rows: EmailDto[]) => {
        const updatedEmails = rows.map(row => this.toView(row));

        this.emails = updatedEmails;

        // Preserve the selected email after refreshing.
        const selectedIndex = this.emails.findIndex(
          email => email.id === this.selectedEmailKey
        );

        if (selectedIndex >= 0) {
          this.selectedEmailId = selectedIndex + 1;
        } else if (this.emails.length > 0) {
          // Select the first email if the previous selection disappeared.
          this.selectedEmailKey = this.emails[0].id;
          this.selectedEmailId = 1;
        } else {
          this.selectedEmailKey = null;
          this.selectedEmailId = 0;
        }

        this.cdr.markForCheck();
      },

      error: error => {
        // Keep the previous data if the API temporarily fails.
        console.error('Failed to load incoming emails:', error);
        this.cdr.markForCheck();
      }
    });
  }

  private toView(r: EmailDto): IncomingEmail {
    const senderEmail = r.senderEmail ?? '';
    const name = senderEmail.split('@')[0] || 'Unknown';

    // Email processing status is separate from quotation request status.
    const status =
      r.status === 'PROCESSED'
        ? 'Processed'
        : 'Pending';

    return {
      id: r.id,
      sender: senderEmail,
      email: senderEmail,
      subject: r.subject ?? '(No subject)',
      category: r.requestId ? 'Quotation' : 'Other',
      status,
      time: shortDate(r.receivedAt),
      initials: initials(name, senderEmail),
      color: avatarColor(r.id),
      body: (r.body ?? '')
        .split(/\r?\n/)
        .filter(line => line.trim().length > 0),
      name,
      detectedEmail: senderEmail
    };
  }

  get filteredEmails(): IncomingEmail[] {
    const search = this.searchText.trim().toLowerCase();

    return this.emails.filter(email => {
      const matchesSearch =
        !search ||
        email.sender.toLowerCase().includes(search) ||
        email.email.toLowerCase().includes(search) ||
        email.subject.toLowerCase().includes(search);

      let matchesFilter = true;

      switch (this.activeFilter) {
        case 'Quotation':
          matchesFilter = email.category === 'Quotation';
          break;

        case 'Other':
          matchesFilter = email.category === 'Other';
          break;

        case 'Pending':
          matchesFilter = email.status === 'Pending';
          break;

        case 'Processed':
          matchesFilter = email.status === 'Processed';
          break;
      }

      return matchesSearch && matchesFilter;
    });
  }

  get selectedEmail(): IncomingEmail | undefined {
    return this.emails.find(
      email => email.id === this.selectedEmailKey
    );
  }

  selectEmail(email: IncomingEmail): void {
    this.selectedEmailKey = email.id;
    this.selectedEmailId = this.emails.indexOf(email) + 1;
  }

  viewEmailDetails(): void {
    const email = this.selectedEmail;

    if (!email) {
      alert('Please select an email first.');
      return;
    }

    alert(
      `Sender: ${email.sender}\n` +
      `Email: ${email.email}\n` +
      `Subject: ${email.subject}\n` +
      `Category: ${email.category}\n` +
      `Status: ${email.status}`
    );
  }

  setFilter(filter: string): void {
    this.activeFilter = filter;
  }

  refreshEmails(): void {
    this.searchText = '';
    this.activeFilter = 'All';
    this.loadEmails();
  }

  // Counts are calculated from the records returned by the backend API.

  get totalEmails(): number {
    return this.emails.length;
  }

  get pendingEmails(): number {
    return this.emails.filter(
      email => email.status === 'Pending'
    ).length;
  }

  get processedEmails(): number {
    return this.emails.filter(
      email => email.status === 'Processed'
    ).length;
  }

  get quotationEmails(): number {
    return this.emails.filter(
      email => email.category === 'Quotation'
    ).length;
  }

  goBack(): void {
    this.location.back();
  }
}
