
import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reports.html',
  styleUrl: './reports.css'
})
export class Reports {

  constructor(private location: Location) {}

  goBack(): void {
    this.location.back();
  }

  totalEmails = 1248;
  quotationRequests = 486;
  quotationsGenerated = 301;
  pendingDetails = 47;
}
