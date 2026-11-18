
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

interface QuotationHistoryEntry {
  quotation: InsuranceQuotation;
  requestId: number;
  policyType: string | null;
  purposeOfTravel: string | null;
}

interface VersionComparisonField {
  field: string;
  label: string;
  previousValue: string;
  updatedValue: string;
  changed: boolean;
}

interface AdditionalDetailItem {
  key: string;
  label: string;
  value: string;
}

interface AdditionalDetailGroup {
  title: string;
  items: AdditionalDetailItem[];
}

type HistoryPopupType = 'requests' | 'quotations' | null;

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

  // CLIENT VERSION HISTORY
  clientVersions: ClientVersionDto[] = [];
  selectedVersion: ClientVersionDto | null = null;
  previousVersion: ClientVersionDto | null = null;

  comparisonPersonalFields: VersionComparisonField[] = [];
  comparisonIdentityFields: VersionComparisonField[] = [];

  // IDENTITY NUMBER REVEAL
  identityNumberVisible = false;
  identityNumberLoading = false;
  identityNumberFull = '';
  identityNumberError = '';

  // REQUEST AND QUOTATION HISTORY POPUP
  historyPopupType: HistoryPopupType = null;

  // INSURANCE DETAILS
  insuranceDetails: ClientInsuranceDetail[] = [];
  insuranceLoading = false;
  insuranceError = '';

  // SEARCH
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
  // VERSION COMPARISON ROWS
  // =========================================

  get comparisonRows(): VersionComparisonField[] {
    return [
      ...this.comparisonPersonalFields,
      ...this.comparisonIdentityFields
    ];
  }

  // =========================================
  // INSURANCE SUMMARY COUNTS
  // =========================================

  get totalInsuranceRequests(): number {
    return this.insuranceDetails.length;
  }

  get totalQuotationVersions(): number {
    return (this.insuranceDetails || []).reduce(
      (total, insurance) =>
        total + (insurance.quotations?.length || 0),
      0
    );
  }

  // =========================================
  // QUOTATION HISTORY
  // =========================================

  get quotationHistoryEntries(): QuotationHistoryEntry[] {
    return (this.insuranceDetails || []).flatMap(insurance =>
      (insurance.quotations || []).map(quotation => ({
        quotation,
        requestId: insurance.requestId,
        policyType: insurance.policyType,
        purposeOfTravel: insurance.purposeOfTravel
      }))
    );
  }

  // =========================================
  // QUOTATIONS SORTED NEWEST FIRST
  // =========================================

  get sortedQuotationHistoryEntries(): QuotationHistoryEntry[] {
    return [...this.quotationHistoryEntries].sort((a, b) => {

      const dateA = a.quotation.createdAt
        ? new Date(a.quotation.createdAt).getTime()
        : 0;

      const dateB = b.quotation.createdAt
        ? new Date(b.quotation.createdAt).getTime()
        : 0;

      const validDateA = Number.isFinite(dateA) ? dateA : 0;
      const validDateB = Number.isFinite(dateB) ? dateB : 0;

      // Latest quotation first
      return validDateB - validDateA;
    });
  }

  // =========================================
  // REQUESTS SORTED NEWEST FIRST
  // =========================================

  get sortedInsuranceDetails(): ClientInsuranceDetail[] {
    return [...(this.insuranceDetails || [])].sort((a, b) => {
      const dateA = a.requestCreatedAt
        ? new Date(a.requestCreatedAt).getTime()
        : 0;

      const dateB = b.requestCreatedAt
        ? new Date(b.requestCreatedAt).getTime()
        : 0;

      const validDateA = Number.isFinite(dateA) ? dateA : 0;
      const validDateB = Number.isFinite(dateB) ? dateB : 0;

      return validDateB - validDateA;
    });
  }

  // =========================================
  // ADDITIONAL DETAIL LABELS
  // =========================================

  private readonly additionalDetailLabels: Record<string, string> = {
    name: 'Applicant Name',
    applicantName: 'Applicant Name',
    email: 'Email Address',
    phone: 'Phone Number',
    phoneNumber: 'Phone Number',

    passportNumber: 'Passport Number',
    identityNumber: 'Identity Document Number',
    documentNumber: 'Document Number',

    policyType: 'Policy Type',
    purposeOfTravel: 'Purpose of Travel',
    originCountry: 'Country of Origin',
    destinationCountry: 'Destination Country',
    travelStartDate: 'Travel Start Date',
    travelEndDate: 'Travel End Date',
    travellers: 'Number of Travellers',
    numberOfTravellers: 'Number of Travellers',

    dateOfBirth: 'Date of Birth',
    gender: 'Gender',
    nationality: 'Nationality',
    countryOfResidence: 'Country of Residence'
  };

  // =========================================
  // NORMALIZE FIELD KEY
  // =========================================

  private normalizeDetailKey(key: string): string {
    return key
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/[_-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .toLowerCase()
      .replace(/\s/g, '');
  }

  // =========================================
  // FORMAT ADDITIONAL DETAIL LABEL
  // =========================================

  formatAdditionalDetailLabel(key: string): string {
    const normalizedKey = this.normalizeDetailKey(key);

    const knownLabel = Object.entries(
      this.additionalDetailLabels
    ).find(
      ([labelKey]) =>
        this.normalizeDetailKey(labelKey) === normalizedKey
    );

    if (knownLabel) {
      return knownLabel[1];
    }

    return key
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/[_-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/\b\w/g, letter => letter.toUpperCase());
  }

  // =========================================
  // FORMAT ADDITIONAL DETAIL VALUE
  // =========================================

  formatAdditionalDetailValue(
    key: string,
    value: unknown
  ): string {

    if (
      value === null ||
      value === undefined ||
      String(value).trim() === ''
    ) {
      return 'Not available';
    }

    const normalizedKey = this.normalizeDetailKey(key);

    if (
      normalizedKey.includes('passportnumber') ||
      normalizedKey.includes('identitynumber') ||
      normalizedKey.includes('documentnumber')
    ) {
      const documentNumber = String(value).trim();

      if (documentNumber.length <= 4) {
        return '••••';
      }

      return '••••' + documentNumber.slice(-4);
    }

    if (
      normalizedKey.includes('date') &&
      /^\d{4}-\d{2}-\d{2}/.test(String(value))
    ) {
      const date = new Date(
        String(value).substring(0, 10) + 'T00:00:00'
      );

      if (!isNaN(date.getTime())) {
        return date.toLocaleDateString('en-GB', {
          day: '2-digit',
          month: 'short',
          year: 'numeric'
        });
      }
    }

    return String(value);
  }

  // =========================================
  // GROUP ADDITIONAL DETAILS
  // =========================================

  getAdditionalDetailGroups(
    details: Record<string, string> | null | undefined
  ): AdditionalDetailGroup[] {

    if (!details) {
      return [];
    }

    const applicantKeys = [
      'name',
      'applicantname',
      'email',
      'phone',
      'phonenumber',
      'dateofbirth',
      'gender',
      'nationality',
      'countryofresidence'
    ];

    const travelKeys = [
      'policytype',
      'purposeoftravel',
      'origincountry',
      'destinationcountry',
      'travelstartdate',
      'travelenddate',
      'travellers',
      'numberoftravellers'
    ];

    const applicant: AdditionalDetailItem[] = [];
    const travel: AdditionalDetailItem[] = [];
    const other: AdditionalDetailItem[] = [];

    Object.entries(details).forEach(([key, value]) => {
      const normalizedKey = this.normalizeDetailKey(key);

      const item: AdditionalDetailItem = {
        key,
        label: this.formatAdditionalDetailLabel(key),
        value: this.formatAdditionalDetailValue(key, value)
      };

      if (applicantKeys.includes(normalizedKey)) {
        applicant.push(item);
      } else if (travelKeys.includes(normalizedKey)) {
        travel.push(item);
      } else {
        other.push(item);
      }
    });

    const groups: AdditionalDetailGroup[] = [];

    if (applicant.length > 0) {
      groups.push({
        title: 'Applicant Details',
        items: applicant
      });
    }

    if (travel.length > 0) {
      groups.push({
        title: 'Travel Details',
        items: travel
      });
    }

    if (other.length > 0) {
      groups.push({
        title: 'Additional Information',
        items: other
      });
    }

    return groups;
  }

  // =========================================
  // REQUEST STATUS DISPLAY LABEL
  // =========================================

  getRequestStatusLabel(
    status: string | null | undefined
  ): string {

    if (!status) {
      return 'Not available';
    }

    return status
      .replace(/[_-]/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, letter => letter.toUpperCase());
  }

  // =========================================
  // REQUEST STATUS CSS CLASS
  // =========================================

  getRequestStatusClass(
    status: string | null | undefined
  ): string {

    const normalized = (status || '').toUpperCase();

    if (
      normalized.includes('MISSING') ||
      normalized.includes('FAILED') ||
      normalized.includes('REJECTED')
    ) {
      return 'request-status-warning';
    }

    if (
      normalized.includes('SENT') ||
      normalized.includes('GENERATED') ||
      normalized.includes('APPROVED')
    ) {
      return 'request-status-success';
    }

    if (
      normalized.includes('WAITING') ||
      normalized.includes('PENDING')
    ) {
      return 'request-status-pending';
    }

    return 'request-status-neutral';
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
        console.error('Unable to load clients:', error);

        this.errorMessage =
          'Unable to load client records. Please try again.';

        this.loading = false;
      }
    });
  }

  // =========================================
  // SEARCH
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
        !name || (client.name || '').toLowerCase().includes(name);

      const matchesPhone =
        !phone || (client.phone || '').toLowerCase().includes(phone);

      const matchesId =
        !id ||
        String(client.id).toLowerCase().includes(id) ||
        (client.clientCode || '').toLowerCase().includes(id);

      const matchesEmail =
        !email || (client.email || '').toLowerCase().includes(email);

      return matchesName && matchesPhone && matchesId && matchesEmail;
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
  // SELECT CLIENT AND OPEN DETAILS
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
    this.insuranceLoading = true;

    this.resetIdentityNumber();
    this.closeHistoryPopup();

    this.popupLoading = true;
    this.errorMessage = '';

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

    this.loadClientVersions(client.id);
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
  // REVEAL OR HIDE COMPLETE IDENTITY NUMBER
  // =========================================

  toggleIdentityNumber(): void {
    if (this.identityNumberVisible) {
      this.identityNumberVisible = false;
      return;
    }

    if (this.identityNumberFull) {
      this.identityNumberVisible = true;
      return;
    }

    if (!this.selectedClient || this.identityNumberLoading) {
      return;
    }

    const clientId = this.selectedClient.id;

    this.identityNumberLoading = true;
    this.identityNumberError = '';

    this.http.get<{ identityNumber: string }>(
      `/api/clients/${clientId}/identity-number`
    ).subscribe({
      next: (response) => {
        if (this.selectedClient?.id !== clientId) {
          return;
        }

        this.identityNumberFull =
          response?.identityNumber || '';

        this.identityNumberVisible =
          !!this.identityNumberFull;

        if (!this.identityNumberFull) {
          this.identityNumberError =
            'Identity number is not available.';
        }

        this.identityNumberLoading = false;
      },
      error: (error) => {
        console.error(
          'Unable to reveal identity number:',
          error
        );

        if (this.selectedClient?.id === clientId) {
          this.identityNumberError =
            error?.status === 404
              ? 'Identity number is not available.'
              : 'Unable to retrieve identity number. Please try again.';

          this.identityNumberLoading = false;
        }
      }
    });
  }

  // =========================================
  // RESET IDENTITY NUMBER
  // =========================================

  resetIdentityNumber(): void {
    this.identityNumberVisible = false;
    this.identityNumberLoading = false;
    this.identityNumberFull = '';
    this.identityNumberError = '';
  }

  // =========================================
  // REQUEST HISTORY POPUP
  // =========================================

  openRequestHistory(): void {
    this.historyPopupType = 'requests';
  }

  // =========================================
  // QUOTATION HISTORY POPUP
  // =========================================

  openQuotationHistory(): void {
    this.historyPopupType = 'quotations';
  }

  // =========================================
  // CLOSE REQUEST / QUOTATION POPUP
  // =========================================

  closeHistoryPopup(): void {
    this.historyPopupType = null;
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
        console.error('Unable to load client versions:', error);

        if (this.selectedClient?.id === clientId) {
          this.clientVersions = [];
        }
      }
    });
  }

  // =========================================
  // OPEN VERSION DETAILS
  // =========================================

  openVersionDetails(version: ClientVersionDto): void {
    this.selectedVersion = version;

    this.previousVersion =
      this.clientVersions
        .filter(item => item.versionNo < version.versionNo)
        .sort((a, b) => b.versionNo - a.versionNo)[0] || null;

    this.comparisonPersonalFields = [];
    this.comparisonIdentityFields = [];

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
      label,
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
  // CLOSE VERSION POPUP
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
    this.closeHistoryPopup();
    this.resetIdentityNumber();

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

  trackByQuotation(
    index: number,
    entry: QuotationHistoryEntry
  ): number {
    return entry.quotation.quotationId;
  }
}
