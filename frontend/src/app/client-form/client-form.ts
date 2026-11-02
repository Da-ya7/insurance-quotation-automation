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
  template: `
  <div class="wrap">
    <h1>Insurance quotation</h1>

    @if (loading()) { <p>Loading…</p> }
    @if (error()) { <p class="err">{{ error() }}</p> }

    @if (result(); as r) {
      <div class="card" [class.ok]="r.status === 'QUOTATION_SENT'">
        <h2>{{ r.status === 'QUOTATION_SENT' ? 'Thank you' : 'Almost there' }}</h2>
        <p>{{ r.message }}</p>
        @if (r.quotationNumber) { <p>Quotation number: <b>{{ r.quotationNumber }}</b></p> }
        @if (r.blockedFields?.length) { <p class="err">Not changed: {{ r.blockedFields!.join(', ') }}</p> }
      </div>
    }

    @if (!type() && !loading() && !result()) {
      <div class="card">
        <h2>Choose insurance type</h2>
        @for (t of types(); track t.code) {
          <button type="button" (click)="pick(t.code)">{{ t.label }}</button>
        }
      </div>
    }

    @if (form() && !result()) {
      <form class="card" (ngSubmit)="submit()">
        <h2>{{ form()!.label }}</h2>

        @if (link()?.existingClient) {
          <p class="note">Welcome back{{ link()!.client?.name ? ', ' + link()!.client!.name : '' }}.
            Your saved details are filled in – please complete only the new information.</p>
          @if (link()!.missingFields.length) {
            <p class="note">Still needed: <b>{{ link()!.missingFields.join(', ') }}</b></p>
          }
          @if (!unlocked()) {
            <div class="otp">
              <p>To change saved personal details, verify the code sent to your registered phone
                {{ link()!.client?.phoneMasked }}.</p>
              @if (!otpSent()) {
                <button type="button" (click)="sendOtp()" [disabled]="busy()">Send code</button>
              } @else {
                <input [(ngModel)]="otpCode" name="otp" placeholder="6-digit code" maxlength="6" />
                <button type="button" (click)="verifyOtp()" [disabled]="busy()">Verify</button>
              }
              @if (otpMsg()) { <p class="note">{{ otpMsg() }}</p> }
            </div>
          }
        }

        <h3>Your details</h3>
        <label>Full name <input [(ngModel)]="a.name" name="name" [disabled]="locked()" required /></label>
        <label>Email <input type="email" [(ngModel)]="a.email" name="email" [disabled]="locked()" required /></label>
        <label>Phone <input [(ngModel)]="a.phone" name="phone" [disabled]="locked()" required /></label>
        <label>Date of birth <input type="date" [(ngModel)]="a.dateOfBirth" name="dob" [disabled]="locked()" /></label>
        <label>Gender
          <select [(ngModel)]="a.gender" name="gender" [disabled]="locked()">
            <option [ngValue]="null">–</option><option>MALE</option><option>FEMALE</option><option>OTHER</option>
          </select></label>
        <label>Nationality <input [(ngModel)]="a.nationality" name="nat" [disabled]="locked()" /></label>
        <label>Country of residence
          <select [(ngModel)]="a.countryOfResidence" name="cor" [disabled]="locked()">
            <option [ngValue]="null">–</option>
            @for (c of countries(); track c.code) { <option [value]="c.code">{{ c.name }}</option> }
          </select></label>

        <h3>Government ID</h3>
        <label>Issuing country
          <select [ngModel]="a.identityCountry" (ngModelChange)="onIdCountry($event)" name="idc" [disabled]="locked()">
            <option [ngValue]="null">–</option>
            @for (c of countries(); track c.code) { <option [value]="c.code">{{ c.name }}</option> }
          </select></label>
        <label>ID type
          <select [(ngModel)]="a.identityType" name="idt" [disabled]="locked()">
            <option [ngValue]="null">–</option>
            @for (t of idTypes(); track t.code) { <option [value]="t.code">{{ t.label }}</option> }
          </select></label>
        <label>ID number
          <input [(ngModel)]="a.identityNumber" name="idn" [disabled]="locked()"
                 [placeholder]="idExample() || (link()?.client?.identityNumberMasked ?? '')" /></label>

        <h3>{{ form()!.label }} details</h3>
        @for (f of otherFields(); track f.fieldName) {
          <label>{{ f.displayLabel }}{{ f.isMandatory ? ' *' : '' }}
            <input [type]="inputType(f.dataType)" [(ngModel)]="values[f.fieldName]" [name]="f.fieldName"
                   [required]="f.isMandatory" [attr.title]="f.helpText" />
          </label>
        }

        <button type="submit" [disabled]="busy()">Get my quotation</button>
      </form>
    }
  </div>`,
  styles: [`
    .wrap{max-width:640px;margin:24px auto;padding:0 16px;font-family:system-ui,sans-serif}
    .card{background:#fff;border:1px solid #dde3ea;border-radius:10px;padding:16px;margin:12px 0}
    .card.ok{border-color:#2e9d5b}
    label{display:block;margin:10px 0 0;font-size:14px}
    input,select{display:block;width:100%;padding:8px;margin-top:4px;box-sizing:border-box}
    button{margin:10px 8px 0 0;padding:9px 16px;cursor:pointer}
    .err{color:#b3261e}.note{color:#44546a}
    .otp{background:#f4f7fb;padding:10px;border-radius:8px;margin:10px 0}
  `]
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
