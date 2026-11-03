
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

import {
  ApiService,
  ClientDto,
  ClientVersionDto
} from '../../core/api.service';

interface InsuranceQuotation {
  quotationId: number;
  quotationNumber: string;
  versionNo: number;
  premiumAmount: number;
  taxAmount: number;
  totalAmount: number;
  currency: string;
  quotationStatus: string;
  createdAt: string | null;
  sentAt: string | null;
}

interface ClientInsuranceDetail {
  requestId: number;
  policyType: string | null;
  purposeOfTravel: string | null;
  requestStatus: string;
  requestCreatedAt: string | null;
  additionalDetails: Record<string, string>;
  quotations: InsuranceQuotation[];
}

@Component({
  selector: 'app-client-master',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './client-master.html',
  styleUrl: './client-master.css'
})
export class ClientMaster implements OnInit {

  clients: ClientDto[] = [];
  filteredClients: ClientDto[] = [];

  selectedClient: ClientDto | null = null;

  clientVersions: ClientVersionDto[] = [];

  // Insurance details
  insuranceDetails: ClientInsuranceDetail[] = [];
  insuranceLoading = false;
  insuranceError = '';

  searchName = '';
  searchPhone = '';
  searchId = '';
  searchEmail = '';

  loading = false;
  popupLoading = false;
  errorMessage = '';

  constructor(
    private api: ApiService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.loading = true;
    this.errorMessage = '';

    this.api.clients().subscribe({
      next: (data: ClientDto[]) => {
        this.clients = data || [];
        this.filteredClients = [...this.clients];
        this.loading = false;
        this.onSearchChange();
      },
      error: (error: unknown) => {
        console.error('Unable to load clients:', error);
        this.errorMessage =
          'Unable to load client records. Please try again.';
        this.loading = false;
      }
    });
  }

  get hasSearchCriteria(): boolean {
    return !!(
      this.searchName.trim() ||
      this.searchPhone.trim() ||
      this.searchId.trim() ||
      this.searchEmail.trim()
    );
  }

  onSearchChange(): void {
    const name = this.searchName.trim().toLowerCase();
    const phone = this.searchPhone.trim().toLowerCase();
    const id = this.searchId.trim().toLowerCase();
    const email = this.searchEmail.trim().toLowerCase();

    this.filteredClients = this.clients.filter(client => {

      const matchesName =
        !name || (client.name || '').toLowerCase().includes(name);

      const matchesPhone =
        !phone || (client.phone || '').toLowerCase().includes(phone);

      const matchesId =
        !id ||
        String(client.id).toLowerCase().includes(id) ||
        (client.clientCode || '').toLowerCase().includes(id);

      const matchesEmail =
        !email || (client.email || '').toLowerCase().includes(email);

      return matchesName &&
        matchesPhone &&
        matchesId &&
        matchesEmail;
    });
  }

  clearSearch(): void {
    this.searchName = '';
    this.searchPhone = '';
    this.searchId = '';
    this.searchEmail = '';

    this.filteredClients = [...this.clients];
  }

  // Open client popup and automatically load all details
  selectClient(client: ClientDto): void {

    this.selectedClient = client;

    this.clientVersions = [];

    this.insuranceDetails = [];
    this.insuranceError = '';

    this.popupLoading = true;
    this.insuranceLoading = true;

    this.errorMessage = '';

    // Load complete client information
    this.api.client(client.id).subscribe({
      next: (data: ClientDto) => {
        if (this.selectedClient?.id === client.id) {
          this.selectedClient = data;
          this.popupLoading = false;
        }
      },
      error: (error: unknown) => {
        console.error('Unable to load client details:', error);

        if (this.selectedClient?.id === client.id) {
          this.popupLoading = false;
        }
      }
    });

    // Automatically load client version history
    this.loadClientVersions(client.id);

    // Automatically load insurance details
    this.loadInsuranceDetails(client.id);
  }

  loadInsuranceDetails(clientId: number): void {

    this.insuranceLoading = true;
    this.insuranceError = '';
    this.insuranceDetails = [];

    this.http.get<ClientInsuranceDetail[]>(
      `/api/clients/${clientId}/insurance-details`
    ).subscribe({

      next: (data: ClientInsuranceDetail[]) => {

        if (this.selectedClient?.id !== clientId) {
          return;
        }

        this.insuranceDetails = data || [];
        this.insuranceLoading = false;
      },

      error: (error: unknown) => {

        console.error(
          'Unable to load client insurance details:',
          error
        );

        if (this.selectedClient?.id === clientId) {
          this.insuranceError =
            'Unable to load insurance details. Please try again.';

          this.insuranceLoading = false;
        }
      }
    });
  }

  loadClientVersions(clientId: number): void {

    this.api.clientVersions(clientId).subscribe({

      next: (data: ClientVersionDto[]) => {

        if (this.selectedClient?.id !== clientId) {
          return;
        }

        this.clientVersions = (data || []).sort(
          (a, b) => b.versionNo - a.versionNo
        );
      },

      error: (error: unknown) => {

        console.error(
          'Unable to load client versions:',
          error
        );

        if (this.selectedClient?.id === clientId) {
          this.clientVersions = [];
        }
      }
    });
  }

  closePopup(): void {

    this.selectedClient = null;

    this.clientVersions = [];

    this.insuranceDetails = [];
    this.insuranceError = '';
    this.insuranceLoading = false;

    this.popupLoading = false;
  }

  getClientInitial(
    name: string | null | undefined
  ): string {
    return name?.trim().charAt(0).toUpperCase() || 'C';
  }

  getIdentityNumber(
    client: ClientDto | ClientVersionDto
  ): string {
    return client.identityNumberMasked || 'Not available';
  }

  getVerificationStatus(
    verified: boolean | null | undefined
  ): string {
    return verified ? 'Verified' : 'Not verified';
  }

  getChangedFields(
    fields: string | string[] | null | undefined
  ): string {

    if (Array.isArray(fields)) {
      return fields.length
        ? fields.join(', ')
        : 'Not available';
    }

    return fields || 'Not available';
  }

  getVersionDate(
    date: string | null | undefined
  ): Date | null {
    return date ? new Date(date) : null;
  }

  // FIX: Safely get object keys inside Angular HTML templates
  getObjectKeys(value: Record<string, any> | null | undefined): string[] {
    return value ? Object.keys(value) : [];
  }

  trackByVersion(
    index: number,
    version: ClientVersionDto
  ): number {
    return version.versionNo;
  }

  trackByInsurance(
    index: number,
    insurance: ClientInsuranceDetail
  ): number {
    return insurance.requestId;
  }
}
