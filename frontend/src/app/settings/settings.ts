import { Component } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { goBackOrHome } from '../core/nav';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [],
  templateUrl: './settings.html',
  styleUrl: './settings.css'
})
export class Settings {
  constructor(private location: Location, private router: Router) {}

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }

  saveSettings(): void {
    alert('Settings saved successfully');
  }
}