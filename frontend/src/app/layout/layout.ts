// import { Component } from '@angular/core';
// import { CommonModule } from '@angular/common';
// import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

// @Component({
//   selector: 'app-layout',
//   standalone: true,
//   imports: [
//     CommonModule,
//     RouterOutlet,
//     RouterLink,
//     RouterLinkActive
//   ],
//   templateUrl: './layout.html',
//   styleUrl: './layout.css'
// })
// export class Layout {

//   sidebarOpen = true;

//   toggleSidebar() {
//     this.sidebarOpen = !this.sidebarOpen;
//   }

//   logout() {
//     console.log('Logout clicked');
//   }
// }

import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  RouterOutlet,
  RouterLink,
  RouterLinkActive
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

  // Sidebar starts open
  sidebarOpen = true;


  // Open / close sidebar
  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }


  // Logout
  logout(): void {
    console.log('Logout clicked');

    // JWT logout will be added later
  }

}