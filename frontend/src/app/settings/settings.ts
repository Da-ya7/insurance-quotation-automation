import { Component } from '@angular/core';
import { Location } from '@angular/common';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [],
  templateUrl: './settings.html',
  styleUrl: './settings.css'
})
export class Settings {
  constructor(private location: Location) {}

  goBack(): void {
    this.location.back();
  }

  saveSettings(): void {
    alert('Settings saved successfully');
  }
}