
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

/* =========================================
   VERSION COMPARISON FIELD
========================================= */

interface VersionComparisonField {
  field: string;
  label: string;
  previousValue: string;
  updatedValue: string;
  changed: boolean;
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

  // =========================================
  // CLIENT VERSION HISTORY
  // =========================================

  clientVersions: ClientVersionDto[] = [];

  selectedVersion: ClientVersionDto | null = null;
  previousVersion: ClientVersionDto | null = null;

  comparisonPersonalFields: VersionComparisonField[] = [];
  comparisonIdentityFields: VersionComparisonField[] = [];

  // Combined rows used by the existing HTML template
  get comparisonRows(): VersionComparisonField[] {
    return [
      ...this.comparisonPersonalFields,
      ...this.comparisonIdentityFields
    ];
  }

  // =========================================
  // INSURANCE DETAILS
  // =========================================

  insuranceDetails: ClientInsuranceDetail[] = [];
  insuranceLoading = false;
  insuranceError = '';

  // =========================================
  // SEARCH
  // =========================================

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

  // =========================================
  // LOAD CLIENTS
  // =========================================

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

        console.error(
          'Unable to load clients:',
          error
        );

        this.errorMessage =
          'Unable to load client records. Please try again.';

        this.loading = false;
      }
    });
  }

  // =========================================
  // SEARCH CLIENTS
  // =========================================

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
        !name ||
        (client.name || '').toLowerCase().includes(name);

      const matchesPhone =
        !phone ||
        (client.phone || '').toLowerCase().includes(phone);

      const matchesId =
        !id ||
        String(client.id).toLowerCase().includes(id) ||
        (client.clientCode || '').toLowerCase().includes(id);

      const matchesEmail =
        !email ||
        (client.email || '').toLowerCase().includes(email);

      return (
        matchesName &&
        matchesPhone &&
        matchesId &&
        matchesEmail
      );
    });
  }

  clearSearch(): void {

    this.searchName = '';
    this.searchPhone = '';
    this.searchId = '';
    this.searchEmail = '';

    this.filteredClients = [...this.clients];
  }

  // =========================================
  // INSURANCE SUMMARY COUNTS
  // =========================================

  get totalInsuranceRequests(): number {
    return this.insuranceDetails.length;
  }

  get totalQuotationVersions(): number {

    return (this.insuranceDetails || []).reduce(
      (total, insurance) => {

        return total + (
          insurance.quotations?.length || 0
        );

      },
      0
    );
  }

  // =========================================
  // SELECT CLIENT AND OPEN POPUP
  // =========================================

  selectClient(client: ClientDto): void {

    this.selectedClient = client;

    this.clientVersions = [];

    this.selectedVersion = null;
    this.previousVersion = null;

    this.comparisonPersonalFields = [];
    this.comparisonIdentityFields = [];

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

        console.error(
          'Unable to load client details:',
          error
        );

        if (this.selectedClient?.id === client.id) {
          this.popupLoading = false;
        }
      }
    });

    // Load selected client's version history
    this.loadClientVersions(client.id);

    // Load only selected client's insurance details
    this.loadInsuranceDetails(client.id);
  }

  // =========================================
  // LOAD SELECTED CLIENT INSURANCE DETAILS
  // =========================================

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

  // =========================================
  // LOAD CLIENT VERSION HISTORY
  // =========================================

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

  // =========================================
  // OPEN VERSION COMPARISON POPUP
  // =========================================

  openVersionDetails(version: ClientVersionDto): void {

    this.selectedVersion = version;

    this.previousVersion =
  this.clientVersions
    .filter(item => item.versionNo < version.versionNo)
    .sort((a, b) => b.versionNo - a.versionNo)[0] || null;

    this.comparisonPersonalFields = [];
    this.comparisonIdentityFields = [];

    // First version has no previous version
    if (!this.previousVersion) {
      return;
    }

    this.buildVersionComparison(
      this.previousVersion,
      version
    );
  }

  // =========================================
  // BUILD VERSION COMPARISON
  // =========================================

  buildVersionComparison(
    previous: ClientVersionDto,
    updated: ClientVersionDto
  ): void {

    this.comparisonPersonalFields = [

      this.createComparisonField(
        'Client Name',
        previous.name,
        updated.name
      ),

      this.createComparisonField(
        'Email',
        previous.email,
        updated.email
      ),

      this.createComparisonField(
        'Phone Number',
        previous.phone,
        updated.phone
      ),

      this.createComparisonField(
        'Date of Birth',
        previous.dateOfBirth,
        updated.dateOfBirth
      ),

      this.createComparisonField(
        'Gender',
        previous.gender,
        updated.gender
      ),

      this.createComparisonField(
        'Nationality',
        previous.nationality,
        updated.nationality
      ),

      this.createComparisonField(
        'Country of Residence',
        previous.countryOfResidence,
        updated.countryOfResidence
      )

    ];

    this.comparisonIdentityFields = [

      this.createComparisonField(
        'Identity Document Type',
        previous.identityType,
        updated.identityType
      ),

      this.createComparisonField(
        'Issuing Country',
        previous.identityCountry,
        updated.identityCountry
      ),

      this.createComparisonField(
        'Identity Document Number',
        this.getIdentityNumber(previous),
        this.getIdentityNumber(updated)
      ),

      this.createComparisonField(
        'Verification Status',
        this.getVerificationStatus(previous.otpVerified),
        this.getVerificationStatus(updated.otpVerified)
      )

    ];
  }

  // =========================================
  // CREATE COMPARISON ROW
  // =========================================

  createComparisonField(
    label: string,
    previousValue: unknown,
    updatedValue: unknown
  ): VersionComparisonField {

    const previousText =
      this.formatComparisonValue(previousValue);

    const updatedText =
      this.formatComparisonValue(updatedValue);

    return {
  field: label,
  label: label,
  previousValue: previousText,
  updatedValue: updatedText,
  changed: this.areValuesDifferent(
    previousText,
    updatedText
  )
};
  }

  // =========================================
  // FORMAT COMPARISON VALUES
  // =========================================

  formatComparisonValue(value: unknown): string {

    if (
      value === null ||
      value === undefined ||
      String(value).trim() === ''
    ) {
      return 'Not available';
    }

    if (typeof value === 'boolean') {
      return value ? 'Yes' : 'No';
    }

    return String(value);
  }

  // =========================================
  // CHECK VALUE CHANGES
  // =========================================

  areValuesDifferent(
    previousValue: string,
    updatedValue: string
  ): boolean {

    return previousValue.trim() !== updatedValue.trim();
  }

  // =========================================
  // COMPARISON COUNTS
  // =========================================

  get totalChangedFields(): number {

    return this.comparisonRows.filter(
      field => field.changed
    ).length;
  }

  get totalComparedFields(): number {
    return this.comparisonRows.length;
  }

  // =========================================
  // CHECK FIRST VERSION
  // =========================================

  get isFirstVersion(): boolean {

    return !!(
      this.selectedVersion &&
      !this.previousVersion
    );
  }

  // =========================================
  // CLOSE VERSION COMPARISON POPUP
  // =========================================

  closeVersionDetails(): void {

    this.selectedVersion = null;
    this.previousVersion = null;

    this.comparisonPersonalFields = [];
    this.comparisonIdentityFields = [];
  }

  // =========================================
  // CLOSE CLIENT POPUP
  // =========================================

  closePopup(): void {

    this.closeVersionDetails();

    this.selectedClient = null;

    this.clientVersions = [];

    this.insuranceDetails = [];
    this.insuranceError = '';
    this.insuranceLoading = false;

    this.popupLoading = false;
  }

  // =========================================
  // CLIENT DISPLAY HELPERS
  // =========================================

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

  // =========================================
  // OBJECT KEY HELPER
  // =========================================

  getObjectKeys(
    value: Record<string, any> | null | undefined
  ): string[] {

    return value ? Object.keys(value) : [];
  }

  // =========================================
  // TRACK BY HELPERS
  // =========================================

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
