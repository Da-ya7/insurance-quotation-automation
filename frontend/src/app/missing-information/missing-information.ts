import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-missing-information',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './missing-information.html',
  styleUrl: './missing-information.css'
})
export class MissingInformation {

  constructor(private router: Router) {}

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  missingItems = [
    {
      client: 'Rahul Nair',
      email: 'rahul@gmail.com',
      missing: 'Travel Dates',
      status: 'Requested'
    },
    {
      client: 'Divya Raj',
      email: 'divya@gmail.com',
      missing: 'Coverage Type',
      status: 'Pending'
    },
    {
      client: 'Priya Sharma',
      email: 'priya@gmail.com',
      missing: 'Traveller Age',
      status: 'Requested'
    }
  ];
}