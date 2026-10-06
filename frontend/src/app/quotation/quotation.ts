import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-quotation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './quotation.html',
  styleUrl: './quotation.css'
})
export class Quotation {

  constructor(private router: Router) {}

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  quotations = [
    {
      number: 'QTN-2026-0042',
      client: 'Arun Kumar',
      destination: 'Singapore',
      premium: '₹4,500',
      status: 'Generated'
    },
    {
      number: 'QTN-2026-0041',
      client: 'Priya Sharma',
      destination: 'Dubai',
      premium: '₹6,200',
      status: 'Processing'
    },
    {
      number: 'QTN-2026-0039',
      client: 'Divya Raj',
      destination: 'Thailand',
      premium: '₹5,100',
      status: 'Generated'
    }
  ];
}