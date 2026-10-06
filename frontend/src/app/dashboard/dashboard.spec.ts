import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard {

  // Dashboard statistics
  totalEmails = 128;
  quotationRequests = 42;
  pendingDetails = 8;
  quotationsGenerated = 36;

  // Recent quotation requests
  quotationRequestsList = [
    {
      id: 'QTN-2026-0042',
      client: 'Arun Kumar',
      email: 'arun@gmail.com',
      destination: 'Singapore',
      travellers: 2,
      status: 'Quotation Generated'
    },
    {
      id: 'QTN-2026-0041',
      client: 'Priya Sharma',
      email: 'priya@gmail.com',
      destination: 'Dubai',
      travellers: 3,
      status: 'Processing'
    },
    {
      id: 'QTN-2026-0040',
      client: 'Rahul Nair',
      email: 'rahul@gmail.com',
      destination: 'Malaysia',
      travellers: 4,
      status: 'Missing Details'
    },
    {
      id: 'QTN-2026-0039',
      client: 'Divya Raj',
      email: 'divya@gmail.com',
      destination: 'Thailand',
      travellers: 2,
      status: 'Quotation Generated'
    }
  ];

  // Email categories
  emailCategories = [
    {
      name: 'Quotation',
      count: 42,
      percentage: 33
    },
    {
      name: 'Policy Renewal',
      count: 31,
      percentage: 24
    },
    {
      name: 'Claim Enquiry',
      count: 24,
      percentage: 19
    },
    {
      name: 'General Enquiry',
      count: 31,
      percentage: 24
    }
  ];

  // Recent activity
  activities = [
    {
      time: '10:42 AM',
      title: 'New quotation email received',
      description: 'Travel insurance request received from Arun Kumar'
    },
    {
      time: '10:35 AM',
      title: 'Information extracted',
      description: 'Destination, travel dates and traveller details extracted'
    },
    {
      time: '10:18 AM',
      title: 'Missing details requested',
      description: 'Email sent to client requesting required information'
    },
    {
      time: '09:54 AM',
      title: 'Quotation generated',
      description: 'QTN-2026-0042 quotation PDF generated'
    }
  ];

  openMenu(menu: string): void {
    console.log(menu);
  }

  logout(): void {
    console.log('Logout clicked');
  }
}