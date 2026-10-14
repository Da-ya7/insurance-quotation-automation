import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';

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
export class Dashboard implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  activeMenu: string = 'Dashboard';

  totalEmails = 0;
  quotationRequests = 0;
  pendingDetails = 0;
  quotationsGenerated = 0;
  quotationsSent = 0;

  selectedTimeRange: string = '30 Days';

  showProfileMenu: boolean = false;

  searchQuery: string = '';

  // ================= SEARCH ITEMS =================

  searchItems = [
    { name: 'Dashboard', route: '/dashboard' },
    { name: 'Incoming Emails', route: '/incoming-emails' },
    { name: 'Quotation Requests', route: '/quotation-requests' },
    { name: 'Missing Information', route: '/missing-information' },
    { name: 'Quotations', route: '/quotations' },
    { name: 'Clients', route: '/clients' },
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

  emailCategories: { name: string; count: number; percentage: number }[] = [];

  ngOnInit(): void {
    this.api.dashboardStats().subscribe({
      next: stats => {
        this.totalEmails = stats.totalEmails;
        this.quotationRequests = stats.quotationRequests;
        this.pendingDetails = stats.pendingDetails;
        this.quotationsGenerated = stats.quotationsGenerated;
        this.quotationsSent = stats.quotationsSent;

        const rows = [
          { name: 'Incoming Emails', count: stats.totalEmails },
          { name: 'Quotation Requests', count: stats.quotationRequests },
          { name: 'Pending Requests', count: stats.pendingDetails },
          { name: 'Quotations Sent', count: stats.quotationsSent }
        ];
        const max = Math.max(1, ...rows.map(r => r.count));
        this.emailCategories = rows.map(r => ({
          ...r,
          percentage: Math.round((r.count / max) * 100)
        }));
        this.cdr.markForCheck();
      },
      error: () => {
        this.emailCategories = [];
        this.cdr.markForCheck();
      }
    });
  }

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
    const confirmed = window.confirm(
      'Are you sure you want to logout?'
    );

    if (confirmed) {
      localStorage.removeItem('token');
      localStorage.removeItem('authToken');
      localStorage.removeItem('isLoggedIn');

      this.showProfileMenu = false;

      this.router.navigate(['/login']);
    }
  }
}
