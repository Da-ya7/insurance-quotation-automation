
import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';

@Component({
  selector: 'app-quotation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './quotation.html',
  styleUrl: './quotation.css'
})
export class Quotation {

  constructor(private location: Location) {}

  goBack(): void {
    this.location.back();
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
