import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-incoming-emails',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './incoming-emails.html',
  styleUrl: './incoming-emails.css'
})
export class IncomingEmails {

  constructor(private router: Router) {}

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  emails = [
    {
      sender: 'Arun Kumar',
      email: 'arun@gmail.com',
      subject: 'Travel Insurance Quotation',
      category: 'Quotation',
      status: 'Processed'
    },
    {
      sender: 'Priya Sharma',
      email: 'priya@gmail.com',
      subject: 'Policy Renewal',
      category: 'Policy Renewal',
      status: 'Processed'
    },
    {
      sender: 'Rahul Nair',
      email: 'rahul@gmail.com',
      subject: 'Travel Insurance Details',
      category: 'Quotation',
      status: 'Pending'
    }
  ];

}