
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  ApiService,
  ClientDto,
  ClientVersionDto
} from '../../core/api.service';

@Component({
  selector: 'app-client-master',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './client-master.html',
  styleUrl: './client-master.css'
})
export class ClientMaster {

  clients: ClientDto[] = [];
  filteredClients: ClientDto[] = [];

  selectedClient: ClientDto | null = null;
  clientVersions: ClientVersionDto[] = [];

  searchText = '';

  loading = false;
  versionsLoading = false;
  clientsLoading = true;

  searchSubmitted = false;
  showPopup = false;

  errorMessage = '';
  versionError = '';

  constructor(private api: ApiService) {
    this.loadClients();
  }

  // Load all clients from the backend
  loadClients(): void {
    this.clientsLoading = true;
    this.errorMessage = '';

    this.api.clients().subscribe({
      next: (data: ClientDto[]) => {
        this.clients = data ?? [];
        this.clientsLoading = false;
        this.filterClients();
      },
      error: (error: unknown) => {
        console.error('Unable to load clients', error);

        this.clientsLoading = false;
        this.errorMessage =
          'Unable to load client details. Please check the connection and try again.';
      }
    });
  }

  // Filter clients while typing
  onSearchChange(): void {
    this.searchSubmitted = false;
    this.errorMessage = '';
    this.filterClients();
  }

  // Search button action
  searchClients(): void {
    if (this.clientsLoading) {
      return;
    }

    this.searchSubmitted = true;
    this.filterClients();

    if (this.searchText.trim() && this.filteredClients.length === 0) {
      this.errorMessage = 'Client Not Found';
    } else {
      this.errorMessage = '';
    }
  }

  // Search by name, phone, email or Client ID
  filterClients(): void {
    const query = this.searchText.trim().toLowerCase();

    if (!query) {
      this.filteredClients = [];
      this.searchSubmitted = false;
      return;
    }

    this.filteredClients = this.clients.filter(client =>
      (client.name ?? '').toLowerCase().includes(query) ||
      (client.phone ?? '').toLowerCase().includes(query) ||
      (client.email ?? '').toLowerCase().includes(query) ||
      (client.clientCode ?? '').toLowerCase().includes(query)
    );
  }

  // Open the selected client's details popup
  selectClient(client: ClientDto): void {
    this.showPopup = true;
    this.loading = true;

    this.selectedClient = client;
    this.clientVersions = [];
    this.versionError = '';

    // Load complete client information
    this.api.client(client.id).subscribe({
      next: (data: ClientDto) => {
        this.selectedClient = data;
        this.loading = false;
      },
      error: (error: unknown) => {
        console.error('Unable to load selected client', error);

        this.loading = false;
        this.selectedClient = client;
      }
    });

    // Load client version history
    this.loadClientVersions(client.id);
  }

  // Load version history for the selected client
  loadClientVersions(clientId: number): void {
    this.versionsLoading = true;
    this.versionError = '';

    this.api.clientVersions(clientId).subscribe({
      next: (data: ClientVersionDto[]) => {
        this.clientVersions = data ?? [];
        this.versionsLoading = false;
      },
      error: (error: unknown) => {
        console.error('Unable to load client versions', error);

        this.versionsLoading = false;
        this.versionError =
          'Client version history could not be loaded.';
      }
    });
  }

  // Close the client details popup
  closePopup(): void {
    this.showPopup = false;
    this.selectedClient = null;
    this.clientVersions = [];

    this.loading = false;
    this.versionsLoading = false;
  }

  // Clear the search field
  clearSearch(): void {
    this.searchText = '';
    this.filteredClients = [];

    this.searchSubmitted = false;
    this.errorMessage = '';
  }

  // Show matching client suggestions while typing
  get showSuggestions(): boolean {
    return (
      this.searchText.trim().length > 0 &&
      this.filteredClients.length > 0 &&
      !this.showPopup
    );
  }
}
