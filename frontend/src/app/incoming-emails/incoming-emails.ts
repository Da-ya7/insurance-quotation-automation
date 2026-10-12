import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';

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
export class IncomingEmails {

  constructor(private location: Location) {}

  searchText = '';
  activeFilter = 'All';
  selectedEmailId = 1;
  private selectedSender = 'Arun Kumar';

  emails: IncomingEmail[] = [
    {
      sender: 'Arun Kumar',
      email: 'arun@gmail.com',
      subject: 'Travel Insurance Quotation',
      category: 'Quotation',
      status: 'Processed',
      time: '10:42 AM',
      initials: 'AK',
      color: '',
      body: [
        'Hello Insurance Team,',
        'I am planning a trip and would like to know about your travel insurance options.',
        'Please provide me with a quotation for travel insurance coverage.',
        'Thank you.',
        'Regards,\nArun Kumar'
      ],
      name: 'Arun Kumar',
      detectedEmail: 'arun@gmail.com'
    },
    {
      sender: 'Priya Sharma',
      email: 'priya@gmail.com',
      subject: 'Policy Renewal Request',
      category: 'Policy Renewal',
      status: 'Processed',
      time: '09:35 AM',
      initials: 'PS',
      color: 'purple',
      body: [
        'Hello Insurance Team,',
        'I would like to renew my existing travel insurance policy.',
        'Please share the renewal details and available options.',
        'Thank you.',
        'Regards,\nPriya Sharma'
      ],
      name: 'Priya Sharma',
      detectedEmail: 'priya@gmail.com'
    },
    {
      sender: 'Rahul Nair',
      email: 'rahul@gmail.com',
      subject: 'Travel Insurance Details',
      category: 'Quotation',
      status: 'Pending',
      time: 'Yesterday',
      initials: 'RN',
      color: 'orange',
      body: [
        'Hello Insurance Team,',
        'I need travel insurance for my upcoming trip.',
        'Please let me know the available plans and coverage.',
        'Thank you.',
        'Regards,\nRahul Nair'
      ],
      name: 'Rahul Nair',
      detectedEmail: 'rahul@gmail.com'
    }
  ];

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

        case 'Renewal':
          matchesFilter = email.category === 'Policy Renewal';
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
