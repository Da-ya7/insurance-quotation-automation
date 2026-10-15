import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, EmailDto } from '../core/api.service';
import { avatarColor, initials, shortDate } from '../core/format';

interface IncomingEmail {
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
  selectedEmailId = 1;
  private selectedSender = '';

  emails: IncomingEmail[] = [];

  private timer: ReturnType<typeof setInterval> | undefined;

  ngOnInit(): void {
    this.loadEmails();
    // New mail arrives in the background: refresh the list every 30 seconds.
    this.timer = setInterval(() => this.loadEmails(), 30000);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  loadEmails(): void {
    this.api.emails().subscribe({
      next: rows => {
        this.emails = rows.map(r => this.toView(r));
        if (this.emails.length > 0 && !this.selectedEmail) {
          this.selectedSender = this.emails[0].sender;
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.emails = [];
        this.cdr.markForCheck();
      }
    });
  }

  private toView(r: EmailDto): IncomingEmail {
    const name = r.senderEmail.split('@')[0];
    const waiting = r.requestStatus === 'MISSING_INFORMATION'
      || r.requestStatus === 'WAITING_FOR_CLIENT';
    const status = r.status === 'PROCESSED' && !waiting ? 'Processed' : 'Pending';

    return {
      sender: r.senderEmail,
      email: r.senderEmail,
      subject: r.subject,
      category: r.requestId ? 'Quotation' : 'Other',
      status,
      time: shortDate(r.receivedAt),
      initials: initials(name, r.senderEmail),
      color: avatarColor(r.id),
      body: (r.body ?? '').split(/\r?\n/).filter(line => line.trim().length > 0),
      name,
      detectedEmail: r.senderEmail
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
      }

      return matchesSearch && matchesFilter;
    });
  }

  get selectedEmail(): IncomingEmail | undefined {
    return this.emails.find(
      email => email.sender === this.selectedSender
    );
  }

  selectEmail(email: IncomingEmail): void {
    this.selectedSender = email.sender;
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
