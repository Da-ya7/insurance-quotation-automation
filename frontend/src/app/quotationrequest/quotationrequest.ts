import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-quotationrequest',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './quotationrequest.html',
  styleUrl: './quotationrequest.css'
})
export class Quotationrequest {

  constructor(private router: Router) {}

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  requests = [
    {
      id: 'REQ-2026-0042',
      client: 'Arun Kumar',
      destination: 'Singapore',
      travellers: 2,
      status: 'Quotation Generated'
    },
    {
      id: 'REQ-2026-0041',
      client: 'Priya Sharma',
      destination: 'Dubai',
      travellers: 3,
      status: 'Processing'
    },
    {
      id: 'REQ-2026-0040',
      client: 'Rahul Nair',
      destination: 'Malaysia',
      travellers: 4,
      status: 'Missing Details'
    }
  ];
}