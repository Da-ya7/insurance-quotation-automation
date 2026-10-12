import { Component } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import {
  RouterOutlet,
  RouterLink,
  RouterLinkActive,
  Router
} from '@angular/router';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive
  ],
  templateUrl: './layout.html',
  styleUrl: './layout.css'
})
export class Layout {

  sidebarOpen = true;

  constructor(
    public router: Router,
    private location: Location
  ) {}

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  goBack(): void {
    this.location.back();
  }

  logout(): void {
    const confirmed = window.confirm(
      'Are you sure you want to logout?'
    );

    if (confirmed) {
      localStorage.removeItem('token');
      localStorage.removeItem('authToken');

      this.router.navigate(['/login']);
    }
  }
}
