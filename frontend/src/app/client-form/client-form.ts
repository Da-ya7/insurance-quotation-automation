import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import {
  ApiService, ApplicantPayload, FormLink, PublicCountry, PublicForm,
  PublicIdentityType, PublicInsuranceType, SubmissionResult
} from '../core/api.service';
import { errorMessage } from '../core/http-error';

const PERSONAL = ['name', 'email', 'phone', 'dateOfBirth', 'gender', 'nationality',
  'countryOfResidence', 'identityCountry', 'identityType', 'identityNumber'] as const;

/**
 * Public customer form. Works for every insurance type (fields come from the
 * database). With a personal link (?token=) a returning client sees saved
 * details, only fills what is missing, and must verify an OTP sent to the
 * phone on file before changing saved personal details.
 */
@Component({
  selector: 'app-client-form',
  imports: [FormsModule],
  templateUrl: './client-form.html',
  styleUrl: './client-form.css'
})
export class ClientForm {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  loading = signal(true);
  busy = signal(false);
  error = signal('');
  result = signal<SubmissionResult | null>(null);

  types = signal<PublicInsuranceType[]>([]);
  countries = signal<PublicCountry[]>([]);
  idTypes = signal<PublicIdentityType[]>([]);
  form = signal<PublicForm | null>(null);
  link = signal<FormLink | null>(null);
  type = signal<string | null>(null);

  otpSent = signal(false);
  otpMsg = signal('');
  otpCode = '';
  unlocked = signal(false);

  token: string | null = null;
  a: ApplicantPayload = {
    name: null, email: null, phone: null, dateOfBirth: null, gender: null, nationality: null,
    countryOfResidence: null, identityCountry: null, identityType: null, identityNumber: null
  };
  values: Record<string, string> = {};

  // saved personal data stays read-only until the phone is verified
  locked = computed(() => !!this.link()?.existingClient && !this.unlocked());
  otherFields = computed(() =>
    (this.form()?.fields ?? []).filter(f => !(PERSONAL as readonly string[]).includes(f.fieldName)));
  idExample = computed(() =>
    this.idTypes().find(t => t.code === this.a.identityType)?.example ?? null);

  constructor() {
    const qp = this.route.snapshot.queryParamMap;
    this.token = qp.get('token');
    const routeType = this.route.snapshot.paramMap.get('type') ?? qp.get('type');

    this.api.publicCountries().subscribe({ next: c => this.countries.set(c) });

    if (this.token) {
      this.api.formLink(this.token).subscribe({
        next: l => {
          this.link.set(l);
          this.applyPrefill(l);
          this.loadForm(l.insuranceType);
        },
        error: e => this.fail(e, 'This link is not valid or has expired.')
      });
    } else if (routeType) {
      this.loadForm(routeType);
    } else {
      this.api.publicInsuranceTypes().subscribe({
        next: t => { this.types.set(t); this.loading.set(false); },
        error: e => this.fail(e)
      });
    }
  }

  pick(code: string) {
    this.router.navigate(['/form', code], this.token ? { queryParams: { token: this.token } } : {});
    this.loading.set(true);
    this.loadForm(code);
  }

  private loadForm(code: string) {
    this.type.set(code);
    this.api.publicForm(code).subscribe({
      next: f => { this.form.set(f); this.loading.set(false); this.refreshIdTypes(); },
      error: e => this.fail(e)
    });
  }

  private applyPrefill(l: FormLink) {
    const c = l.client;
    if (c) {
      this.a.name = c.name; this.a.email = c.email; this.a.dateOfBirth = c.dateOfBirth;
      this.a.gender = c.gender; this.a.nationality = c.nationality;
      this.a.countryOfResidence = c.countryOfResidence;
      this.a.identityCountry = c.identityCountry; this.a.identityType = c.identityType;
      this.a.phone = c.phoneMasked; // masked; the server keeps the saved number unless changed after OTP
    }
    for (const [k, v] of Object.entries(l.prefill ?? {})) {
      if ((PERSONAL as readonly string[]).includes(k)) {
        (this.a as unknown as Record<string, string | null>)[k] = v;
      } else {
        this.values[k] = v;
      }
    }
  }

  onIdCountry(code: string | null) {
    this.a.identityCountry = code;
    this.a.identityType = null;
    this.refreshIdTypes();
  }

  private refreshIdTypes() {
    if (!this.a.identityCountry) { this.idTypes.set([]); return; }
    this.api.publicIdentityTypes(this.a.identityCountry).subscribe({
      next: t => this.idTypes.set(t), error: () => this.idTypes.set([])
    });
  }

  inputType(t: string) {
    return t === 'DATE' ? 'date' : t === 'NUMBER' ? 'number' : t === 'EMAIL' ? 'email' : 'text';
  }

  sendOtp() {
    if (!this.token) return;
    this.busy.set(true);
    this.api.sendOtp(this.token).subscribe({
      next: r => { this.otpSent.set(true); this.otpMsg.set('Code sent to ' + r.phoneMasked + '.'); this.busy.set(false); },
      error: e => { this.otpMsg.set(errorMessage(e)); this.busy.set(false); }
    });
  }

  verifyOtp() {
    if (!this.token) return;
    this.busy.set(true);
    this.api.verifyOtp(this.token, this.otpCode.trim()).subscribe({
      next: r => {
        this.busy.set(false);
        if (r.verified) { this.unlocked.set(true); this.otpMsg.set('Verified. You can now edit your details.'); }
        else { this.otpMsg.set(r.message || 'Incorrect code.'); }
      },
      error: e => { this.otpMsg.set(errorMessage(e)); this.busy.set(false); }
    });
  }

  submit() {
    const code = this.type();
    if (!code) return;
    this.busy.set(true);
    this.error.set('');
    const applicant = { ...this.a };
    // untouched masked values are never sent back as if they were real data
    if (this.locked()) {
      applicant.phone = null; applicant.identityNumber = null;
    }
    this.api.submitForm(code, this.token, { insuranceType: code, applicant, fields: this.values }).subscribe({
      next: r => { this.result.set(r); this.busy.set(false); },
      error: e => { this.error.set(errorMessage(e)); this.busy.set(false); }
    });
  }

  private fail(e: unknown, fallback?: string) {
    this.error.set(errorMessage(e, fallback));
    this.loading.set(false);
  }
}
