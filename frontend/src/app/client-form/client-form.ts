import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';


import {
  ApiService,
  ApplicantPayload,
  ExistingCustomerProfile,
  FormLink,
  PublicCountry,
  PublicForm,
  PublicIdentityType,
  PublicInsuranceType,
  SubmissionResult
} from '../core/api.service';
import { errorMessage } from '../core/http-error';

const PERSONAL = [
  'name', 'email', 'phone', 'dateOfBirth', 'gender', 'nationality',
  'countryOfResidence', 'identityCountry', 'identityType', 'identityNumber'
] as const;

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

  // A customer cannot see the application form until OTP verification succeeds.
  unlocked = signal(false);

  // New-customer OTP challenge and verification token.
  challengeId = signal<string | null>(null);
  verificationToken = signal<string | null>(null);
  customerType = signal<'NEW' | 'EXISTING' | 'AMBIGUOUS' | null>(null);


  token: string | null = null;

  a: ApplicantPayload = {
    name: null,
    email: null,
    phone: null,
    dateOfBirth: null,
    gender: null,
    nationality: null,
    countryOfResidence: null,
    identityCountry: null,
    identityType: null,
    identityNumber: null
  };

  values: Record<string, string> = {};

  locked = computed(() =>
    !!this.link()?.existingClient && !this.unlocked()
  );

  otherFields = computed(() =>
    (this.form()?.fields ?? []).filter(
      f => !(PERSONAL as readonly string[]).includes(f.fieldName)
    )
  );

  idExample = computed(() =>
    this.idTypes().find(t => t.code === this.a.identityType)?.example ?? null
  );

  constructor() {
    const qp = this.route.snapshot.queryParamMap;
    this.token = qp.get('token');

    const routeType =
      this.route.snapshot.paramMap.get('type') ?? qp.get('type');

    this.api.publicCountries().subscribe({
      next: c => this.countries.set(c)
    });

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
        next: t => {
          this.types.set(t);
          this.loading.set(false);
        },
        error: e => this.fail(e)
      });
    }
  }

  pick(code: string) {
    this.unlocked.set(false);
    this.otpSent.set(false);
    this.otpMsg.set('');
    this.otpCode = '';
    this.challengeId.set(null);
    this.verificationToken.set(null);

    this.router.navigate(['/form', code]);
    this.loading.set(true);
    this.loadForm(code);
  }

  private loadForm(code: string) {
    this.type.set(code);
    this.api.publicForm(code).subscribe({
      next: f => {
        this.form.set(f);
        this.loading.set(false);
        this.refreshIdTypes();
      },
      error: e => this.fail(e)
    });
  }

  private applyPrefill(l: FormLink) {
    const c = l.client;

    if (c) {
      this.a.name = c.name;
      this.a.email = c.email;
      this.a.dateOfBirth = c.dateOfBirth;
      this.a.gender = c.gender;
      this.a.nationality = c.nationality;
      this.a.countryOfResidence = c.countryOfResidence;
      this.a.identityCountry = c.identityCountry;
      this.a.identityType = c.identityType;

      // Never put the masked phone into the actual applicant phone field.
      this.a.phone = null;
    }

    for (const [k, v] of Object.entries(l.prefill ?? {})) {
      if (l.existingClient && k === 'phone') {
        continue;
      }

      if ((PERSONAL as readonly string[]).includes(k)) {
        (this.a as unknown as Record<string, string | null>)[k] = v;
      } else {
        this.values[k] = v;
      }
    }
  }

  private applyNewCustomerPrefill(c: ExistingCustomerProfile) {
    // Preserve the phone number entered and verified by the customer.
    const verifiedPhone = this.a.phone;

    this.a.name = c.name;
    this.a.email = c.email;
    this.a.phone = verifiedPhone;
    this.a.dateOfBirth = c.dateOfBirth;
    this.a.gender = c.gender;
    this.a.nationality = c.nationality;
    this.a.countryOfResidence = c.countryOfResidence;
    this.a.identityCountry = c.identityCountry;
    this.a.identityType = c.identityType;

    // Never prefill a masked ID as if it were the actual ID number.
    this.a.identityNumber = null;

    // Load the identity types for the customer's saved identity country.
    this.refreshIdTypes();
  }

  onNewPhoneChange() {
    // A newly entered phone number requires a fresh OTP challenge.
    if (!this.token) {
      this.otpSent.set(false);
      this.otpMsg.set('');
      this.otpCode = '';
      this.challengeId.set(null);
      this.verificationToken.set(null);
      this.unlocked.set(false);
      this.customerType.set(null);
    }
  }

  sendOtp() {
    this.busy.set(true);
    this.otpMsg.set('');
    this.error.set('');

    // Returning customer: OTP goes to the phone associated with the link.
    if (this.token) {
      this.api.sendOtp(this.token).subscribe({
        next: r => {
          this.otpSent.set(true);
          this.otpMsg.set(
            'Verification code sent to ' + r.phoneMasked + '.'
          );
          this.busy.set(false);
        },
        error: e => {
          this.otpMsg.set(errorMessage(e));
          this.busy.set(false);
        }
      });

      return;
    }

    // New customer: OTP goes to the phone entered on this screen.
    const phone = this.a.phone?.trim();

    if (!phone) {
      this.otpMsg.set('Please enter your mobile number first.');
      this.busy.set(false);
      return;
    }

    this.api.sendNewCustomerOtp(phone).subscribe({
      next: r => {
        this.challengeId.set(r.challengeId);
        this.otpSent.set(true);
        this.otpMsg.set(
          'Verification code sent to ' + r.phoneMasked + '.'
        );
        this.busy.set(false);
      },
      error: e => {
        this.otpMsg.set(errorMessage(e));
        this.busy.set(false);
      }
    });
  }

  verifyOtp() {
    const code = this.otpCode.trim();

    if (!/^\d{6}$/.test(code)) {
      this.otpMsg.set('Please enter the 6-digit verification code.');
      return;
    }

    this.busy.set(true);
    this.otpMsg.set('');

    // ------------------------------------------------------------
    // Returning customer: preserve the existing email form-link flow.
    // ------------------------------------------------------------
    if (this.token) {
      this.api.verifyOtp(this.token, code).subscribe({
        next: r => {
          this.busy.set(false);

          if (r.verified) {
            this.unlocked.set(true);
            this.otpMsg.set('Phone verified successfully.');
          } else {
            this.otpMsg.set(
              r.message || 'Incorrect verification code.'
            );
          }
        },
        error: e => {
          this.otpMsg.set(errorMessage(e));
          this.busy.set(false);
        }
      });

      return;
    }

    // ------------------------------------------------------------
    // New customer: verify OTP and identify Client Master record.
    // ------------------------------------------------------------
    const challenge = this.challengeId();

    if (!challenge) {
      this.otpMsg.set('Please request a new verification code.');
      this.busy.set(false);
      return;
    }

    this.api.verifyNewCustomerOtp(challenge, code).subscribe({
      next: r => {
        this.busy.set(false);

        if (!r.verified || !r.verificationToken) {
          this.otpMsg.set(
            r.message || 'Incorrect verification code.'
          );
          return;
        }

        // Save the backend-issued token for quotation submission.
        this.verificationToken.set(r.verificationToken);

        // --------------------------------------------------------
        // CASE 1: Existing customer found.
        // --------------------------------------------------------
        if (r.customerType === 'EXISTING' && r.client) {
          this.customerType.set('EXISTING');

          this.applyNewCustomerPrefill(r.client);

          this.unlocked.set(true);

          this.otpMsg.set(
            'Phone verified. Your existing customer details have been loaded. Please review them and complete your insurance details.'
          );

          return;
        }

        // --------------------------------------------------------
        // CASE 2: New customer.
        // --------------------------------------------------------
        if (r.customerType === 'NEW') {
          this.customerType.set('NEW');

          this.unlocked.set(true);

          this.otpMsg.set(
            'Phone verified successfully. Please complete your customer details and insurance information.'
          );

          return;
        }

        // --------------------------------------------------------
        // CASE 3: Multiple customers share the same phone.
        // Do not disclose or select any customer profile.
        // --------------------------------------------------------
        if (r.customerType === 'AMBIGUOUS') {
          this.customerType.set('AMBIGUOUS');

          this.verificationToken.set(null);
          this.unlocked.set(false);

          this.otpMsg.set(
            'This phone number is associated with multiple customer records. We cannot safely identify your profile automatically. Please contact our team for assistance.'
          );

          return;
        }

        // Unexpected or incomplete backend response: fail closed.
        this.verificationToken.set(null);
        this.unlocked.set(false);

        this.otpMsg.set(
          'We could not identify your customer record. Please contact our team for assistance.'
        );
      },

      error: e => {
        this.otpMsg.set(errorMessage(e));
        this.busy.set(false);
      }
    });
  }

  onIdCountry(code: string | null) {
    this.a.identityCountry = code;
    this.a.identityType = null;
    this.refreshIdTypes();
  }

  private refreshIdTypes() {
    if (!this.a.identityCountry) {
      this.idTypes.set([]);
      return;
    }

    this.api.publicIdentityTypes(this.a.identityCountry).subscribe({
      next: t => this.idTypes.set(t),
      error: () => this.idTypes.set([])
    });
  }

  inputType(t: string) {
    return t === 'DATE'
      ? 'date'
      : t === 'NUMBER'
        ? 'number'
        : t === 'EMAIL'
          ? 'email'
          : 'text';
  }

  submit() {
    const code = this.type();

    if (!code) return;

    if (!this.unlocked()) {
      this.error.set('Please verify your phone number before submitting.');
      return;
    }

    if (!this.token && !this.verificationToken()) {
      this.error.set('Your phone verification has expired. Please verify again.');
      this.unlocked.set(false);
      return;
    }

    this.busy.set(true);
    this.error.set('');

    const applicant = { ...this.a };
    const fields = this.values;

    const readField = (...names: string[]): string => {
      for (const name of names) {
        const value = fields[name]?.trim();
        if (value) return value;
      }
      return '';
    };

    const verification = !this.token && this.verificationToken()
      ? { verificationToken: this.verificationToken()! }
      : {};

    if (code.toUpperCase() === 'TRAVEL') {

      const passportNumber =
        readField('passportNumber') ||
        (this.a.identityType?.toUpperCase() === 'PASSPORT'
          ? this.a.identityNumber?.trim() || ''
          : '');

      const travelPayload = {
        insuranceType: code,

        applicant: {
          ...applicant,
          passportNumber
        },

        // Backend expects these at the TOP LEVEL.
        departureCountry: readField(
          'departureCountry',
          'originCountry'
        ),

        destinationCountry: readField('destinationCountry'),

        travelStartDate: readField('travelStartDate'),

        travelEndDate: readField('travelEndDate'),

        purposeOfTravel: readField('purposeOfTravel'),

        additionalTravellers: [],

        ...verification
      };

      const missing: string[] = [];

      if (!travelPayload.departureCountry)
        missing.push('Departure country');

      if (!travelPayload.destinationCountry)
        missing.push('Destination country');

      if (!travelPayload.travelStartDate)
        missing.push('Travel start date');

      if (!travelPayload.travelEndDate)
        missing.push('Travel end date');

      if (!travelPayload.purposeOfTravel)
        missing.push('Purpose of travel');

      if (!travelPayload.applicant.passportNumber)
        missing.push('Passport number');

      if (missing.length) {
        this.error.set(
          'Please complete these required fields: ' + missing.join(', ')
        );
        this.busy.set(false);
        return;
      }

      this.api.submitQuotationForm(
        travelPayload,
        this.token
      ).subscribe({
        next: r => {
          this.result.set(r);
          this.busy.set(false);
        },
        error: e => {
          this.error.set(errorMessage(e));
          this.busy.set(false);
        }
      });

      return;
    }

    // Keep the existing generic submission flow for other insurance types.
    const payload = {
      insuranceType: code,
      applicant,
      fields,
      ...verification
    };

    this.api.submitForm(code, this.token, payload).subscribe({
      next: r => {
        this.result.set(r);
        this.busy.set(false);
      },
      error: e => {
        this.error.set(errorMessage(e));
        this.busy.set(false);
      }
    });
  }

  private fail(e: unknown, fallback?: string) {
    this.error.set(errorMessage(e, fallback));
    this.loading.set(false);
  }
}