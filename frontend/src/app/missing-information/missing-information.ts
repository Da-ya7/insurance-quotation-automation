
import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';

@Component({
  selector: 'app-missing-information',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './missing-information.html',
  styleUrl: './missing-information.css'
})
export class MissingInformation {

  constructor(private location: Location) {}

  goBack(): void {
    this.location.back();
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
