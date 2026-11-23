import {
  Component,
  OnInit,
  ChangeDetectorRef,
  inject
} from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../core/api.service';

type Decision = 'ACCEPT' | 'REJECT';

@Component({
  selector: 'app-quotation-response',
  standalone: true,
  templateUrl: './quotation-response.html',
  styleUrl: './quotation-response.css'
})
export class QuotationResponse implements OnInit {

  private route = inject(ActivatedRoute);
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  token = '';
  decision: Decision | null = null;

  loading = false;
  completed = false;

  error = '';
  resultMessage = '';

  ngOnInit(): void {

    this.token = this.route.snapshot.paramMap.get('token') ?? '';

    const decisionParam =
      this.route.snapshot.queryParamMap.get('decision')?.toUpperCase();

    if (decisionParam === 'ACCEPT' || decisionParam === 'REJECT') {
      this.decision = decisionParam;
    }

    console.log('Quotation Response Page Loaded');
    console.log('Token:', this.token);
    console.log('Decision:', this.decision);

    if (!this.token || !this.decision) {

      this.error = 'This quotation response link is invalid or incomplete.';
      this.loading = false;

      this.cdr.detectChanges();

      return;
    }

    // Automatically process the decision when the email link is opened.
    this.submitResponse();
  }

  get isAccept(): boolean {
    return this.decision === 'ACCEPT';
  }

  get isReject(): boolean {
    return this.decision === 'REJECT';
  }

  submitResponse(): void {

    if (
      !this.token ||
      !this.decision ||
      this.loading ||
      this.completed
    ) {
      return;
    }

    this.loading = true;
    this.completed = false;
    this.error = '';
    this.resultMessage = '';

    this.cdr.detectChanges();

    console.log('Submitting quotation response...');
    console.log('Decision:', this.decision);

    this.api
      .quotationResponse(this.token, this.decision)
      .subscribe({

        // --------------------------------------------------
        // SUCCESS
        // --------------------------------------------------
        next: (message: string) => {

          console.log('QUOTATION RESPONSE SUCCESS:', message);

          this.loading = false;
          this.completed = true;
          this.error = '';

          this.resultMessage =
            message ||
            (this.isAccept
              ? 'Your quotation has been accepted successfully.'
              : 'Your quotation has been rejected successfully.');

          this.cdr.detectChanges();

          console.log('SUCCESS STATE:', {
            loading: this.loading,
            completed: this.completed,
            error: this.error,
            resultMessage: this.resultMessage
          });
        },

        // --------------------------------------------------
        // ERROR
        // --------------------------------------------------
        error: (err) => {

          console.error('QUOTATION RESPONSE ERROR:', err);
          console.error('HTTP STATUS:', err?.status);
          console.error('ERROR BODY:', err?.error);

          this.loading = false;
          this.completed = false;

          let serverMessage = '';

          if (typeof err?.error === 'string') {
            serverMessage = err.error;
          } else if (err?.error?.message) {
            serverMessage = err.error.message;
          } else if (err?.message) {
            serverMessage = err.message;
          }

          if (err?.status === 409) {

            this.error = serverMessage ||
              'You have already responded to this quotation. Your previous response has been recorded and cannot be changed.';

          } else if (err?.status === 404) {

            this.error =
              serverMessage ||
              'This quotation response link is invalid or expired.';

          } else {

            this.error = serverMessage ||
              'We could not process your response. Please contact our team.';
          }

          this.cdr.detectChanges();

          console.log('ERROR STATE:', {
            loading: this.loading,
            completed: this.completed,
            error: this.error
          });
        }
      });
  }
}