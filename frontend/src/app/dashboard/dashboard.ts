import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard {

  activeMenu: string = 'Dashboard';

  totalEmails = 1248;
  quotationRequests = 486;
  activeClients = 842;
  pendingDetails = 47;
  quotationsGenerated = 301;

  selectedTimeRange: string = '30 Days';

  showProfileMenu: boolean = false;

  searchQuery: string = '';

  // ================= SEARCH ITEMS =================

  searchItems = [
    { name: 'Dashboard', route: '/dashboard' },
    { name: 'Incoming Emails', route: '/incoming-emails' },
    { name: 'Quotation Requests', route: '/quotation-requests' },
    { name: 'Missing Information', route: '/missing-information' },
    { name: 'Quotations', route: '/quotation' },
    { name: 'Clients', route: '/client' },
    { name: 'Reports', route: '/reports' },
    { name: 'Settings', route: '/settings' }
  ];

  // ================= FILTER SEARCH RESULTS =================

  get filteredItems() {

    const search = this.searchQuery.toLowerCase().trim();

    if (!search) {
      return [];
    }

    return this.searchItems.filter(item =>
      item.name.toLowerCase().includes(search)
    );
  }

  // ================= EMAIL CATEGORIES =================

  emailCategories = [
    { name: 'Quotation', count: 42, percentage: 33 },
    { name: 'Policy Renewal', count: 31, percentage: 24 },
    { name: 'Claim Enquiry', count: 24, percentage: 19 },
    { name: 'General Enquiry', count: 31, percentage: 24 }
  ];

  // ================= ACTIVITIES =================

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

  // ================= QUOTATION REQUESTS =================

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

  // ================= CONSTRUCTOR =================

  constructor(private router: Router) {}

  // ================= NAVIGATION =================

  goToPage(route: string): void {
    this.router.navigate([route]);
  }

  // ================= TIME RANGE =================

  setTimeRange(range: string): void {
    this.selectedTimeRange = range;
  }

  // ================= PROFILE MENU =================

  toggleProfileMenu(): void {
    this.showProfileMenu = !this.showProfileMenu;
  }

  // ================= LOGOUT =================

  logout(): void {
    localStorage.removeItem('isLoggedIn');
    this.router.navigate(['/login']);
  }
}