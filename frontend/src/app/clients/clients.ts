import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, ClientDto } from '../core/api.service';
import { avatarColor, initials } from '../core/format';

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './clients.html',
  styleUrl: './clients.css'
})
export class Clients implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  constructor(private location: Location) {}

  clients: ClientDto[] = [];
  searchText = '';
  loading = true;
  errorMessage = '';

  readonly initials = initials;
  readonly avatarColor = avatarColor;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';

    this.api.clients().subscribe({
      next: data => {
        this.clients = data;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = 'Could not load clients.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  get filteredClients(): ClientDto[] {
    const q = this.searchText.trim().toLowerCase();
    return this.clients.filter(c =>
      !q ||
      (c.name ?? '').toLowerCase().includes(q) ||
      c.email.toLowerCase().includes(q) ||
      (c.phone ?? '').includes(q)
    );
  }

  goBack(): void {
    this.location.back();
  }
}
