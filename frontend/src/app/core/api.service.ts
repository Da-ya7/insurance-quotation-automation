
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

// All calls use a relative /api path. In development, proxy.conf.json
// forwards /api to Spring Boot, so the browser only ever uses ONE port.

// ------------------------------------------------------------------ auth
export interface LoginResponse {
  token: string;
  role: 'ADMIN' | 'OPERATIONS' | string;
  name: string;
}

// ------------------------------------------------------------------ clients
export interface ClientDto {
  id: number;
  clientCode: string;
  name: string | null;
  email: string;
  phone: string | null;
  phoneVerified: boolean;
  dateOfBirth: string | null;
  gender: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  identityCountry: string | null;
  identityType: string | null;
  identityNumberMasked: string | null;
  versionNo: number;
  requestCount: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface ClientVersionDto {
  versionNo: number;
  name: string | null;
  email: string | null;
  phone: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  identityCountry: string | null;
  identityType: string | null;
  identityNumberMasked: string | null;
  changeSource: string | null;
  changedFields: string | null;
  changedBy: string | null;
  otpVerified: boolean;
  createdAt: string;
}

export interface ClientUpdate {
  name?: string | null;
  email?: string | null;
  phone?: string | null;
  dateOfBirth?: string | null;
  gender?: string | null;
  nationality?: string | null;
  countryOfResidence?: string | null;
  identityCountry?: string | null;
  identityType?: string | null;
  identityNumber?: string | null;
}

// ------------------------------------------------------------------ requests
export interface RequestDto {
  id: number;
  reference: string;
  clientId: number;
  clientCode: string | null;
  clientName: string | null;
  clientEmail: string;
  policyType: string | null;
  insuranceType: string | null;
  purposeOfTravel: string | null;
  destination: string | null;
  origin: string | null;
  travelStartDate: string | null;
  travelEndDate: string | null;
  travellers: number | null;
  status: string;
  statusLabel: string;
  statusGroup: string;
  missingFields: string[];
  quotationNumber: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface TravellerDetail {
  number: number;
  name: string | null;
  dateOfBirth: string | null;
  age: number | null;
  passportNumber: string | null;
  gender: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  email: string | null;
  phone: string | null;
  preExistingMedicalConditions: string | null;
}

export interface RequestDetailDto extends RequestDto {
  clientPhone: string | null;
  fields: Record<string, string>;
  travellerDetails: TravellerDetail[];
  quotationId: number | null;
  totalAmount: number | null;
  emails: {
    id: number;
    subject: string;
    kind: string | null;
    kindLabel: string | null;
    receivedAt: string;
    status: string;
  }[];
  quotations: {
    id: number;
    quotationNumber: string;
    versionNo: number;
    status: string;
    total: number;
    createdAt: string;
    sentAt: string | null;
  }[];
  missing: { field: string; fieldName: string; status: string }[];
}

export interface RequestSummary {
  total: number;
  processing: number;
  missingDetails: number;
  sent: number;
  needsAttention: number;
}

// ------------------------------------------------------------------ emails
export interface EmailDto {
  id: number;
  senderEmail: string;
  subject: string;
  body: string;
  status: string;
  statusLabel: string;
  kind: string | null;
  kindLabel: string | null;
  retryCount: number;
  lastError: string | null;
  receivedAt: string;
  requestId: number | null;
  requestReference: string | null;
  requestStatus: string | null;
  requestStatusLabel: string | null;
  clientId: number | null;
  clientCode: string | null;
  clientName: string | null;
  insuranceType: string | null;
  quotationNumber: string | null;
}

export interface EmailSummary {
  total: number;
  pending: number;
  processed: number;
  failed: number;
  ignored: number;
  newRequests: number;
  replies: number;
  unrelated: number;
  quotations: number;
}

// ------------------------------------------------------------------ missing info
export interface MissingDto {
  id: number;
  requestId: number;
  requestReference: string | null;
  requestStatus: string | null;
  requestStatusLabel: string | null;
  clientId: number | null;
  clientCode: string | null;
  clientName: string | null;
  clientEmail: string;
  insuranceType: string | null;
  field: string;
  fieldName: string;
  status: string;
  createdAt: string;
  resolvedAt: string | null;
}

export interface MissingSummary {
  open: number;
  resolved: number;
  requestsWaiting: number;
}

// ------------------------------------------------------------------ quotations
export interface QuotationDto {
  id: number;
  quotationNumber: string;
  versionNo: number;
  requestId: number;
  requestReference: string | null;
  clientId: number | null;
  clientCode: string | null;
  clientName: string | null;
  clientEmail: string;
  insuranceType: string | null;
  premium: number;
  tax: number;
  total: number;
  currency: string;
  status: string;
  statusLabel: string;
  sent: boolean;
  hasPdf: boolean;
  createdAt: string;
  sentAt: string | null;
}

export interface QuotationDetailDto extends QuotationDto {
  items: { description: string; amount: number }[];
}

export interface QuotationHistoryDto {
  id: number;
  quotationNumber: string;
  versionNo: number;
  status: string;
  total: number;
  clientVersionNo: number | null;
  createdAt: string;
  sentAt: string | null;
}

export interface QuotationSummary {
  total: number;
  sent: number;
  notSent: number;
  superseded: number;
}

// ------------------------------------------------------------------ dashboard / reports
export interface DashboardStats {
  totalEmails: number;
  newRequestEmails: number;
  replyEmails: number;
  unrelatedEmails: number;
  failedEmails: number;
  pendingEmails: number;
  processedEmails: number;
  quotationRequests: number;
  requestsProcessing: number;
  requestsMissing: number;
  requestsSent: number;
  requestsNeedAttention: number;
  clients: number;
  quotationsTotal: number;
  quotationsSent: number;
  quotationsNotSent: number;
  openMissingFields: number;
  dailyRequests: { date: string; requests: number; quotations: number }[];
  requestsByGroup: { group: string; label: string; count: number }[];
  pendingDetails: number;
  quotationsGenerated: number;
  needsAttention: number;
}

export interface ReportSummary {
  requestsByStatus: Record<string, number>;
  totalQuotationValue: number;
  generatedAt: string;
}

// ------------------------------------------------------------------ master data
export interface InsuranceTypeDto {
  code: string;
  label: string;
  keywords: string | null;
  basePremium: number | null;
  ratePercent: number | null;
  rateBasisField: string | null;
  active: boolean;
  sortOrder: number;
}

export interface CountryDto {
  code: string;
  name: string;
  active: boolean;
}

export interface IdentityTypeDto {
  id: number;
  countryCode: string;
  code: string;
  label: string;
  validationRegex: string | null;
  example: string | null;
  active: boolean;
}

export type FieldDataType = 'TEXT' | 'DATE' | 'NUMBER' | 'EMAIL' | 'PHONE';

export interface RequiredFieldDto {
  id: number;
  fieldName: string;
  displayLabel: string;
  dataType: FieldDataType;
  policyType: string | null;
  isActive: boolean;
  isMandatory: boolean;
  validationRegex: string | null;
  sortOrder: number;
  helpText: string | null;
}

export interface EmailKeywordDto {
  id: number;
  keyword: string;
  active: boolean;
}

// ------------------------------------------------------------------ public
export interface SubmissionResult {
  requestId: number;
  status: string;
  statusLabel?: string;
  quotationNumber: string | null;
  message: string;
  missingFields?: string[];
  blockedFields?: string[];
}

export interface PublicInsuranceType {
  code: string;
  label: string;
}

export interface PublicCountry {
  code: string;
  name: string;
}

export interface PublicIdentityType {
  code: string;
  label: string;
  example: string | null;
}

export interface PublicFormField {
  fieldName: string;
  displayLabel: string;
  dataType: FieldDataType;
  isMandatory: boolean;
  helpText: string | null;
  sortOrder: number;
}

export interface PublicForm {
  insuranceType: string;
  label: string;
  fields: PublicFormField[];
}

export interface FormLinkClient {
  clientCode: string;
  name: string | null;
  email: string | null;
  phoneMasked: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  identityCountry: string | null;
  identityType: string | null;
  identityNumberMasked: string | null;
}

export interface FormLink {
  valid: boolean;
  insuranceType: string;
  insuranceTypeLabel: string;
  requestId: number | null;
  existingClient: boolean;
  client: FormLinkClient | null;
  missingFields: string[];
  prefill: Record<string, string>;
}

export interface OtpSendResult {
  sent: boolean;
  phoneMasked: string;
  expiresInSeconds: number;
}

export interface OtpVerifyResult {
  verified: boolean;
  attemptsLeft?: number;
  message?: string;
}

export interface NewCustomerOtpSendResult {
  sent: boolean;
  challengeId: string;
  phoneMasked: string;
  expiresInSeconds: number;
}

/**
 * Public profile returned only after successful phone OTP verification.
 * Sensitive identity numbers are intentionally masked.
 */
export interface ExistingCustomerProfile {
  clientId: number;
  clientCode: string;
  name: string | null;
  email: string | null;
  phoneMasked: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  identityCountry: string | null;
  identityType: string | null;
  identityNumberMasked: string | null;
}

/**
 * Result of verifying a new-customer OTP:
 * NEW       - no matching Client Master record
 * EXISTING  - exactly one matching Client Master record
 * AMBIGUOUS - multiple Client Master records share this phone
 */
export interface NewCustomerOtpVerifyResult {
  verified: boolean;
  verificationToken?: string;
  attemptsLeft?: number;
  message?: string;
  customerType?: 'NEW' | 'EXISTING' | 'AMBIGUOUS';
  client?: ExistingCustomerProfile | null;
}

export interface ProfileEditOtpSendResult {
  sent: boolean;
  challengeId: string;
  phoneMasked: string;
  expiresInSeconds: number;
  message?: string;
}

export interface ProfileEditOtpVerifyResult {
  verified: boolean;
  verificationToken?: string;
  attemptsLeft?: number;
  message?: string;
}

export interface ApplicantPayload {
  name: string | null;
  email: string | null;
  phone: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  nationality: string | null;
  countryOfResidence: string | null;
  identityCountry: string | null;
  identityType: string | null;
  identityNumber: string | null;
}

export interface FormSubmitPayload {
  insuranceType: string;
  applicant: ApplicantPayload;
  fields: Record<string, string>;
  verificationToken?: string;
}

function params(obj: Record<string, string | null | undefined>): HttpParams {
  let p = new HttpParams();

  for (const [k, v] of Object.entries(obj)) {
    if (v !== null && v !== undefined && v !== '') {
      p = p.set(k, v);
    }
  }

  return p;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // ---- auth
  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password });
  }

  // ---- dashboard / reports
  dashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>('/api/dashboard/stats');
  }

  reportSummary(): Observable<ReportSummary> {
    return this.http.get<ReportSummary>('/api/reports/summary');
  }

  runAutomation(): Observable<unknown> {
    return this.http.post('/api/automation/process', {});
  }

  // ---- emails
  emails(kind?: string | null, status?: string | null): Observable<EmailDto[]> {
    return this.http.get<EmailDto[]>('/api/emails', {
      params: params({ kind, status })
    });
  }

  emailSummary(): Observable<EmailSummary> {
    return this.http.get<EmailSummary>('/api/emails/summary');
  }

  // ---- requests
  requests(status?: string | null, group?: string | null): Observable<RequestDto[]> {
    return this.http.get<RequestDto[]>('/api/requests', {
      params: params({ status, group })
    });
  }

  requestSummary(): Observable<RequestSummary> {
    return this.http.get<RequestSummary>('/api/requests/summary');
  }

  request(id: number): Observable<RequestDetailDto> {
    return this.http.get<RequestDetailDto>(`/api/requests/${id}`);
  }

  // ---- missing information
  missingInformation(status: string = 'OPEN'): Observable<MissingDto[]> {
    return this.http.get<MissingDto[]>('/api/missing-information', {
      params: params({ status })
    });
  }

  missingSummary(): Observable<MissingSummary> {
    return this.http.get<MissingSummary>('/api/missing-information/summary');
  }

  // ---- quotations
  quotations(status?: string | null): Observable<QuotationDto[]> {
    return this.http.get<QuotationDto[]>('/api/quotations', {
      params: params({ status })
    });
  }

  quotationSummary(): Observable<QuotationSummary> {
    return this.http.get<QuotationSummary>('/api/quotations/summary');
  }

  quotation(id: number): Observable<QuotationDetailDto> {
    return this.http.get<QuotationDetailDto>(`/api/quotations/${id}`);
  }

  quotationHistory(id: number): Observable<QuotationHistoryDto[]> {
    return this.http.get<QuotationHistoryDto[]>(`/api/quotations/${id}/history`);
  }

  quotationPdf(id: number): Observable<Blob> {
    return this.http.get(`/api/quotations/${id}/pdf`, {
      responseType: 'blob'
    });
  }

  // ---- clients
  clients(): Observable<ClientDto[]> {
    return this.http.get<ClientDto[]>('/api/clients');
  }

  client(id: number): Observable<ClientDto> {
    return this.http.get<ClientDto>(`/api/clients/${id}`);
  }

  clientVersions(id: number): Observable<ClientVersionDto[]> {
    return this.http.get<ClientVersionDto[]>(`/api/clients/${id}/versions`);
  }

  updateClient(id: number, body: ClientUpdate): Observable<ClientDto> {
    return this.http.put<ClientDto>(`/api/clients/${id}`, body);
  }

  // ---- master data (staff)
  insuranceTypes(): Observable<InsuranceTypeDto[]> {
    return this.http.get<InsuranceTypeDto[]>('/api/master/insurance-types');
  }

  createInsuranceType(body: Partial<InsuranceTypeDto>): Observable<InsuranceTypeDto> {
    return this.http.post<InsuranceTypeDto>('/api/master/insurance-types', body);
  }

  updateInsuranceType(code: string, body: Partial<InsuranceTypeDto>): Observable<InsuranceTypeDto> {
    return this.http.put<InsuranceTypeDto>(
      `/api/master/insurance-types/${encodeURIComponent(code)}`,
      body
    );
  }

  countries(): Observable<CountryDto[]> {
    return this.http.get<CountryDto[]>('/api/master/countries');
  }

  updateCountry(code: string, body: { name: string; active: boolean }): Observable<CountryDto> {
    return this.http.put<CountryDto>(
      `/api/master/countries/${encodeURIComponent(code)}`,
      body
    );
  }

  identityTypes(country?: string | null): Observable<IdentityTypeDto[]> {
    return this.http.get<IdentityTypeDto[]>('/api/master/identity-types', {
      params: params({ country })
    });
  }

  createIdentityType(body: Partial<IdentityTypeDto>): Observable<IdentityTypeDto> {
    return this.http.post<IdentityTypeDto>('/api/master/identity-types', body);
  }

  updateIdentityType(id: number, body: Partial<IdentityTypeDto>): Observable<IdentityTypeDto> {
    return this.http.put<IdentityTypeDto>(`/api/master/identity-types/${id}`, body);
  }

  requiredFields(insuranceType?: string | null): Observable<RequiredFieldDto[]> {
    return this.http.get<RequiredFieldDto[]>('/api/master/required-fields', {
      params: params({ insuranceType })
    });
  }

  createRequiredField(body: Partial<RequiredFieldDto>): Observable<RequiredFieldDto> {
    return this.http.post<RequiredFieldDto>('/api/master/required-fields', body);
  }

  updateRequiredField(id: number, body: Partial<RequiredFieldDto>): Observable<RequiredFieldDto> {
    return this.http.put<RequiredFieldDto>(`/api/master/required-fields/${id}`, body);
  }

  emailKeywords(): Observable<EmailKeywordDto[]> {
    return this.http.get<EmailKeywordDto[]>('/api/master/email-keywords');
  }

  createEmailKeyword(keyword: string): Observable<EmailKeywordDto> {
    return this.http.post<EmailKeywordDto>('/api/master/email-keywords', { keyword });
  }

  updateEmailKeyword(id: number, active: boolean): Observable<EmailKeywordDto> {
    return this.http.put<EmailKeywordDto>(`/api/master/email-keywords/${id}`, { active });
  }

  deleteEmailKeyword(id: number): Observable<unknown> {
    return this.http.delete(`/api/master/email-keywords/${id}`);
  }

  // ---- public (no login)

  // ---- new customer phone verification
  sendNewCustomerOtp(phone: string): Observable<NewCustomerOtpSendResult> {
    return this.http.post<NewCustomerOtpSendResult>(
      '/api/public/phone-otp/send',
      { phone }
    );
  }

  sendProfileEditOtp(
    verificationToken: string
  ): Observable<ProfileEditOtpSendResult> {
    return this.http.post<ProfileEditOtpSendResult>(
      '/api/public/profile-edit-otp/send',
      { verificationToken }
    );
  }

  verifyProfileEditOtp(
    verificationToken: string,
    challengeId: string,
    code: string
  ): Observable<ProfileEditOtpVerifyResult> {
    return this.http.post<ProfileEditOtpVerifyResult>(
      '/api/public/profile-edit-otp/verify',
      {
        verificationToken,
        challengeId,
        code
      }
    );
  }

  verifyNewCustomerOtp(
    challengeId: string,
    code: string
  ): Observable<NewCustomerOtpVerifyResult> {
    return this.http.post<NewCustomerOtpVerifyResult>(
      '/api/public/phone-otp/verify',
      { challengeId, code }
    );
  }

  publicInsuranceTypes(): Observable<PublicInsuranceType[]> {
    return this.http.get<PublicInsuranceType[]>('/api/public/master/insurance-types');
  }

  publicCountries(): Observable<PublicCountry[]> {
    return this.http.get<PublicCountry[]>('/api/public/master/countries');
  }

  publicIdentityTypes(country: string): Observable<PublicIdentityType[]> {
    return this.http.get<PublicIdentityType[]>(
      '/api/public/master/identity-types',
      { params: params({ country }) }
    );
  }

  publicForm(type: string): Observable<PublicForm> {
    return this.http.get<PublicForm>(
      `/api/public/forms/${encodeURIComponent(type)}`
    );
  }

  formLink(token: string): Observable<FormLink> {
    return this.http.get<FormLink>(
      `/api/public/form-links/${encodeURIComponent(token)}`
    );
  }

  sendOtp(token: string): Observable<OtpSendResult> {
    return this.http.post<OtpSendResult>(
      `/api/public/form-links/${encodeURIComponent(token)}/otp/send`,
      {}
    );
  }

  verifyOtp(token: string, code: string): Observable<OtpVerifyResult> {
    return this.http.post<OtpVerifyResult>(
      `/api/public/form-links/${encodeURIComponent(token)}/otp/verify`,
      { code }
    );
  }

  submitForm(
    type: string,
    token: string | null,
    payload: FormSubmitPayload
  ): Observable<SubmissionResult> {
    const url = token
      ? `/api/public/form-links/${encodeURIComponent(token)}/submit`
      : `/api/public/forms/${encodeURIComponent(type)}/submit`;

    return this.http.post<SubmissionResult>(url, payload);
  }

  submitQuotationForm(
    payload: unknown,
    token?: string | null
  ): Observable<SubmissionResult> {
    return this.http.post<SubmissionResult>(
      '/api/public/quotation-requests',
      payload,
      { params: params({ token }) }
    );
  }
}
