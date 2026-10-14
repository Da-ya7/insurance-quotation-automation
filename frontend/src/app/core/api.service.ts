import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

// All calls use a relative /api path. In development, proxy.conf.json
// forwards /api to Spring Boot, so the browser only ever uses ONE port.

export interface ClientDto {
  id: number;
  name: string | null;
  email: string;
  phone: string | null;
}

export interface RequestDto {
  id: number;
  reference: string;
  clientId: number;
  clientName: string | null;
  clientEmail: string;
  policyType: string | null;
  purposeOfTravel: string | null;
  destination: string | null;
  origin: string | null;
  travelStartDate: string | null;
  travelEndDate: string | null;
  travellers: number | null;
  status: string;
  createdAt: string;
  updatedAt: string | null;
}

export interface EmailDto {
  id: number;
  senderEmail: string;
  subject: string;
  body: string;
  status: string;
  receivedAt: string;
  requestId: number | null;
  requestStatus: string | null;
}

export interface MissingDto {
  id: number;
  requestId: number;
  clientName: string | null;
  clientEmail: string;
  field: string;
  status: string;
  createdAt: string;
}

export interface QuotationDto {
  id: number;
  quotationNumber: string;
  requestId: number;
  clientName: string | null;
  clientEmail: string;
  premium: number;
  tax: number;
  total: number;
  currency: string;
  status: string;
  hasPdf: boolean;
  createdAt: string;
  sentAt: string | null;
}

export interface DashboardStats {
  totalEmails: number;
  quotationRequests: number;
  clients: number;
  pendingDetails: number;
  quotationsGenerated: number;
  quotationsSent: number;
  needsAttention: number;
}

export interface ReportSummary {
  requestsByStatus: Record<string, number>;
  totalQuotationValue: number;
  generatedAt: string;
}

export interface SubmissionResult {
  requestId: number;
  status: string;
  quotationNumber: string | null;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  login(email: string, password: string): Observable<{ token: string }> {
    return this.http.post<{ token: string }>('/api/auth/login', { email, password });
  }

  submitQuotationForm(payload: unknown): Observable<SubmissionResult> {
    return this.http.post<SubmissionResult>('/api/public/quotation-requests', payload);
  }

  clients(): Observable<ClientDto[]> {
    return this.http.get<ClientDto[]>('/api/clients');
  }

  requests(): Observable<RequestDto[]> {
    return this.http.get<RequestDto[]>('/api/requests');
  }

  emails(): Observable<EmailDto[]> {
    return this.http.get<EmailDto[]>('/api/emails');
  }

  missingInformation(): Observable<MissingDto[]> {
    return this.http.get<MissingDto[]>('/api/missing-information');
  }

  quotations(): Observable<QuotationDto[]> {
    return this.http.get<QuotationDto[]>('/api/quotations');
  }

  quotationPdf(id: number): Observable<Blob> {
    return this.http.get(`/api/quotations/${id}/pdf`, { responseType: 'blob' });
  }

  dashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>('/api/dashboard/stats');
  }

  reportSummary(): Observable<ReportSummary> {
    return this.http.get<ReportSummary>('/api/reports/summary');
  }

  runAutomation(): Observable<unknown> {
    return this.http.post('/api/automation/process', {});
  }
}
