
import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-quotationrequest',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './quotationrequest.html',
  styleUrl: './quotationrequest.css'
})
export class Quotationrequest {

  constructor(private location: Location) {}

  goBack(): void {
    this.location.back();
  }

  searchText = '';
  selectedStatus = 'All Status';

  requests = [
    {
      id: 'REQ-2026-0042',
      client: 'Arun Kumar',
      email: 'arun@gmail.com',
      initials: 'AK',
      avatarClass: '',
      destination: 'Singapore',
      travellers: 2,
      status: 'Quotation Generated'
    },
    {
      id: 'REQ-2026-0041',
      client: 'Priya Sharma',
      email: 'priya@gmail.com',
      initials: 'PS',
      avatarClass: 'purple',
      destination: 'Dubai',
      travellers: 3,
      status: 'Processing'
    },
    {
      id: 'REQ-2026-0040',
      client: 'Rahul Nair',
      email: 'rahul@gmail.com',
      initials: 'RN',
      avatarClass: 'orange',
      destination: 'Malaysia',
      travellers: 4,
      status: 'Missing Details'
    },
    {
      id: 'REQ-2026-0039',
      client: 'Swetha Kumar',
      email: 'swetha@gmail.com',
      initials: 'SK',
      avatarClass: 'green',
      destination: 'Thailand',
      travellers: 2,
      status: 'Quotation Generated'
    }
  ];

  get filteredRequests() {
    const search = this.searchText.trim().toLowerCase();

    return this.requests.filter(request => {
      const matchesSearch =
        request.id.toLowerCase().includes(search) ||
        request.client.toLowerCase().includes(search) ||
        request.destination.toLowerCase().includes(search) ||
        request.email.toLowerCase().includes(search);

      const matchesStatus =
        this.selectedStatus === 'All Status' ||
        request.status === this.selectedStatus;

      return matchesSearch && matchesStatus;
    });
  }

  refreshRequests(): void {
    this.searchText = '';
    this.selectedStatus = 'All Status';
  }

  viewRequest(request: typeof this.requests[number]): void {
    alert(
      `Request ID: ${request.id}\n` +
      `Client: ${request.client}\n` +
      `Destination: ${request.destination}\n` +
      `Travellers: ${request.travellers}\n` +
      `Status: ${request.status}`
    );
  }
}
