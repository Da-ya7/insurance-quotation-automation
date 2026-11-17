// import { Component, computed, inject, signal } from '@angular/core';
// import { FormsModule } from '@angular/forms';
// import { ActivatedRoute, Router } from '@angular/router';

// import {
//   ApiService, ApplicantPayload, FormLink, PublicCountry, PublicForm,
//   PublicIdentityType, PublicInsuranceType, SubmissionResult
// } from '../core/api.service';
// import { errorMessage } from '../core/http-error';

// const PERSONAL = [
//   'name', 'email', 'phone', 'dateOfBirth', 'gender', 'nationality',
//   'countryOfResidence', 'identityCountry', 'identityType', 'identityNumber'
// ] as const;

// @Component({
//   selector: 'app-client-form',
//   imports: [FormsModule],
//   templateUrl: './client-form.html',
//   styleUrl: './client-form.css'
// })
// export class ClientForm {
//   private api = inject(ApiService);
//   private route = inject(ActivatedRoute);
//   private router = inject(Router);

//   loading = signal(true);
//   busy = signal(false);
//   error = signal('');
//   result = signal<SubmissionResult | null>(null);

//   types = signal<PublicInsuranceType[]>([]);
//   countries = signal<PublicCountry[]>([]);
//   idTypes = signal<PublicIdentityType[]>([]);
//   form = signal<PublicForm | null>(null);
//   link = signal<FormLink | null>(null);
//   type = signal<string | null>(null);

//   otpSent = signal(false);
//   otpMsg = signal('');
//   otpCode = '';

//   // The application form remains locked until phone verification succeeds.
//   unlocked = signal(false);

//   // New-customer OTP challenge and verification token.
//   challengeId = signal<string | null>(null);
//   verificationToken = signal<string | null>(null);

//   // Tracks whether personal details were loaded from an existing client.
//   private loadedExistingClient = false;

//   token: string | null = null;

//   a: ApplicantPayload = {
//     name: null,
//     email: null,
//     phone: null,
//     dateOfBirth: null,
//     gender: null,
//     nationality: null,
//     countryOfResidence: null,
//     identityCountry: null,
//     identityType: null,
//     identityNumber: null
//   };

//   values: Record<string, string> = {};

//   locked = computed(() =>
//     !!this.link()?.existingClient && !this.unlocked()
//   );

//   // Traveller count is displayed separately for travel insurance.
//   // This prevents a duplicate if the backend also returns that field.
//   otherFields = computed(() =>
//     (this.form()?.fields ?? []).filter(
//       f =>
//         !(PERSONAL as readonly string[]).includes(f.fieldName) &&
//         !(this.type() === 'TRAVEL' && f.fieldName === 'travellers')
//     )
//   );

//   idExample = computed(() =>
//     this.idTypes().find(t => t.code === this.a.identityType)?.example ?? null
//   );

//   constructor() {
//     const qp = this.route.snapshot.queryParamMap;
//     this.token = qp.get('token');

//     const routeType =
//       this.route.snapshot.paramMap.get('type') ?? qp.get('type');

//     this.api.publicCountries().subscribe({
//       next: c => {
//         this.countries.set(c);

//         // Retry country matching when the country list becomes available.
//         this.syncCountryOfResidence();
//       }
//     });

//     if (this.token) {
//       this.api.formLink(this.token).subscribe({
//         next: l => {
//           this.link.set(l);
//           this.applyPrefill(l);
//           this.loadForm(l.insuranceType);
//         },
//         error: e => this.fail(e, 'This link is not valid or has expired.')
//       });
//     } else if (routeType) {
//       this.loadForm(routeType);
//     } else {
//       this.api.publicInsuranceTypes().subscribe({
//         next: t => {
//           this.types.set(t);
//           this.loading.set(false);
//         },
//         error: e => this.fail(e)
//       });
//     }
//   }

//   /**
//    * Matches the saved country against both country code and country name.
//    *
//    * Examples:
//    * india -> IN
//    * India -> IN
//    * IN    -> IN
//    */
//   private syncCountryOfResidence() {
//     const savedCountry = this.a.countryOfResidence;

//     if (
//       savedCountry === null ||
//       savedCountry === undefined ||
//       String(savedCountry).trim() === ''
//     ) {
//       return;
//     }

//     const countryValue = String(savedCountry).trim().toLowerCase();

//     const matchedCountry = this.countries().find(country => {
//       const countryCode = String(country.code ?? '')
//         .trim()
//         .toLowerCase();

//       const countryName = String(country.name ?? '')
//         .trim()
//         .toLowerCase();

//       return (
//         countryCode === countryValue ||
//         countryName === countryValue
//       );
//     });

//     if (matchedCountry) {
//       this.a.countryOfResidence = matchedCountry.code;
//     }
//   }

//   pick(code: string) {
//     this.unlocked.set(false);
//     this.otpSent.set(false);
//     this.otpMsg.set('');
//     this.otpCode = '';
//     this.challengeId.set(null);
//     this.verificationToken.set(null);
//     this.loadedExistingClient = false;

//     this.router.navigate(['/form', code]);
//     this.loading.set(true);
//     this.loadForm(code);
//   }

//   private loadForm(code: string) {
//     this.type.set(code);

//     this.api.publicForm(code).subscribe({
//       next: f => {
//         this.form.set(f);

//         // Default travel group size to one.
//         // Preserve any existing value supplied through a form link.
//         if (
//           code.toUpperCase() === 'TRAVEL' &&
//           (
//             this.values['travellers'] === undefined ||
//             String(this.values['travellers']).trim() === ''
//           )
//         ) {
//           this.values['travellers'] = '1';
//         }

//         this.loading.set(false);
//         this.refreshIdTypes();
//       },
//       error: e => this.fail(e)
//     });
//   }

//   private applyPrefill(l: FormLink) {
//     const c = l.client;

//     if (c) {
//       this.a.name = c.name;
//       this.a.email = c.email;
//       this.a.dateOfBirth = c.dateOfBirth;
//       this.a.gender = c.gender;
//       this.a.nationality = c.nationality;
//       this.a.countryOfResidence = c.countryOfResidence;

//       this.a.identityCountry = c.identityCountry;
//       this.a.identityType = c.identityType;

//       // Never put the masked phone into the actual applicant phone field.
//       this.a.phone = null;
//     }

//     for (const [k, v] of Object.entries(l.prefill ?? {})) {
//       if (l.existingClient && k === 'phone') {
//         continue;
//       }

//       if ((PERSONAL as readonly string[]).includes(k)) {
//         (this.a as unknown as Record<string, string | null>)[k] = v;
//       } else {
//         this.values[k] = v;
//       }
//     }

//     // Match after all prefilled values have been assigned.
//     this.syncCountryOfResidence();
//   }

//   onNewPhoneChange() {
//     // A different phone number requires a fresh OTP challenge.
//     if (!this.token) {
//       this.otpSent.set(false);
//       this.otpMsg.set('');
//       this.otpCode = '';
//       this.challengeId.set(null);
//       this.verificationToken.set(null);
//       this.unlocked.set(false);

//       // Do not leave another customer's saved details on screen.
//       if (this.loadedExistingClient) {
//         this.a.name = null;
//         this.a.email = null;
//         this.a.dateOfBirth = null;
//         this.a.gender = null;
//         this.a.nationality = null;
//         this.a.countryOfResidence = null;
//         this.a.identityCountry = null;
//         this.a.identityType = null;
//         this.a.identityNumber = null;

//         this.loadedExistingClient = false;
//         this.refreshIdTypes();
//       }
//     }
//   }

//   sendOtp() {
//     this.busy.set(true);
//     this.otpMsg.set('');
//     this.error.set('');

//     // Returning customer: OTP goes to the phone associated with the link.
//     if (this.token) {
//       this.api.sendOtp(this.token).subscribe({
//         next: r => {
//           this.otpSent.set(true);
//           this.otpMsg.set(
//             'Verification code sent to ' + r.phoneMasked + '.'
//           );
//           this.busy.set(false);
//         },
//         error: e => {
//           this.otpMsg.set(errorMessage(e));
//           this.busy.set(false);
//         }
//       });

//       return;
//     }

//     // New or existing customer: OTP goes to the entered phone number.
//     const phone = this.a.phone?.trim();

//     if (!phone) {
//       this.otpMsg.set('Please enter your mobile number first.');
//       this.busy.set(false);
//       return;
//     }

//     this.api.sendNewCustomerOtp(phone).subscribe({
//       next: r => {
//         this.challengeId.set(r.challengeId);
//         this.otpSent.set(true);
//         this.otpMsg.set(
//           'Verification code sent to ' + r.phoneMasked + '.'
//         );
//         this.busy.set(false);
//       },
//       error: e => {
//         this.otpMsg.set(errorMessage(e));
//         this.busy.set(false);
//       }
//     });
//   }

//   verifyOtp() {
//     const code = this.otpCode.trim();

//     if (!/^\d{6}$/.test(code)) {
//       this.otpMsg.set('Please enter the 6-digit verification code.');
//       return;
//     }

//     this.busy.set(true);
//     this.otpMsg.set('');

//     // Returning customer: preserve the existing form-link OTP flow.
//     if (this.token) {
//       this.api.verifyOtp(this.token, code).subscribe({
//         next: r => {
//           this.busy.set(false);

//           if (r.verified) {
//             this.unlocked.set(true);
//             this.otpMsg.set('Phone verified successfully.');
//           } else {
//             this.otpMsg.set(r.message || 'Incorrect verification code.');
//           }
//         },
//         error: e => {
//           this.otpMsg.set(errorMessage(e));
//           this.busy.set(false);
//         }
//       });

//       return;
//     }

//     // Verify the phone challenge for a new or existing customer.
//     const challenge = this.challengeId();

//     if (!challenge) {
//       this.otpMsg.set('Please request a new verification code.');
//       this.busy.set(false);
//       return;
//     }

//     this.api.verifyNewCustomerOtp(challenge, code).subscribe({
//       next: r => {
//         this.busy.set(false);

//         if (!r.verified || !r.verificationToken) {
//           this.otpMsg.set(r.message || 'Incorrect verification code.');
//           return;
//         }

//         this.verificationToken.set(r.verificationToken);
//         this.unlocked.set(true);

//         // Existing customer: automatically load saved personal details.
//         if (r.existingClient && r.client) {
//           this.a.name = r.client.name;
//           this.a.email = r.client.email;

//           // Keep the actual phone number entered and verified by OTP.
//           this.a.dateOfBirth = r.client.dateOfBirth;
//           this.a.gender = r.client.gender;
//           this.a.nationality = r.client.nationality;
//           this.a.countryOfResidence = r.client.countryOfResidence;

//           // Match country name or code with the dropdown options.
//           this.syncCountryOfResidence();

//           this.a.identityCountry = r.client.identityCountry;
//           this.a.identityType = r.client.identityType;

//           // A masked identity number is not the actual identity number.
//           this.a.identityNumber = null;

//           this.loadedExistingClient = true;
//           this.refreshIdTypes();

//           this.otpMsg.set(
//             'Phone verified successfully. Your existing personal details have been loaded.'
//           );
//         } else if (r.ambiguousPhone) {
//           this.otpMsg.set(
//             'Phone verified, but multiple customer records match this number. Please contact the support team.'
//           );
//         } else {
//           // New customer: leave personal details available for manual entry.
//           this.loadedExistingClient = false;
//           this.otpMsg.set(
//             'Phone verified successfully. Please enter your personal details.'
//           );
//         }
//       },
//       error: e => {
//         this.otpMsg.set(errorMessage(e));
//         this.busy.set(false);
//       }
//     });
//   }

//   onIdCountry(code: string | null) {
//     this.a.identityCountry = code;
//     this.a.identityType = null;
//     this.refreshIdTypes();
//   }

//   private refreshIdTypes() {
//     if (!this.a.identityCountry) {
//       this.idTypes.set([]);
//       return;
//     }

//     this.api.publicIdentityTypes(this.a.identityCountry).subscribe({
//       next: t => this.idTypes.set(t),
//       error: () => this.idTypes.set([])
//     });
//   }

//   inputType(t: string) {
//     return t === 'DATE'
//       ? 'date'
//       : t === 'NUMBER'
//         ? 'number'
//         : t === 'EMAIL'
//           ? 'email'
//           : 'text';
//   }

//   submit() {
//     const code = this.type();

//     if (!code) return;

//     if (!this.unlocked()) {
//       this.error.set('Please verify your phone number before submitting.');
//       return;
//     }

//     this.busy.set(true);
//     this.error.set('');

//     // TRAVEL INSURANCE: one applicant's details plus the group count.
//     if (code.toUpperCase() === 'TRAVEL') {
//       const rawTravellerCount = this.values['travellers'];

//       const travellerCount = Number(
//         rawTravellerCount === undefined ||
//         rawTravellerCount === null ||
//         String(rawTravellerCount).trim() === ''
//           ? 1
//           : rawTravellerCount
//       );

//       if (
//         !Number.isInteger(travellerCount) ||
//         travellerCount < 1 ||
//         travellerCount > 20
//       ) {
//         this.error.set('Please enter a valid number of travellers (1-20).');
//         this.busy.set(false);
//         return;
//       }

//       const travelApplicant = {
//         name: this.a.name,
//         email: this.a.email,
//         phone: this.a.phone,
//         dateOfBirth: this.a.dateOfBirth,
//         gender: this.a.gender,
//         nationality: this.a.nationality,
//         countryOfResidence: this.a.countryOfResidence,
//         passportNumber: this.values['passportNumber']
//       };

//       const travelPayload = {
//         applicant: travelApplicant,
//         additionalTravellers: [],
//         travellers: travellerCount,
//         departureCountry: this.values['originCountry'],
//         destinationCountry: this.values['destinationCountry'],
//         travelStartDate: this.values['travelStartDate'],
//         travelEndDate: this.values['travelEndDate'],
//         purposeOfTravel: this.values['purposeOfTravel'],
//         ...(!this.token
//           ? { verificationToken: this.verificationToken() ?? undefined }
//           : {})
//       };

//       this.api.submitQuotationForm(travelPayload, this.token).subscribe({
//         next: r => {
//           this.result.set(r);
//           this.busy.set(false);
//         },
//         error: e => {
//           this.error.set(errorMessage(e));
//           this.busy.set(false);
//         }
//       });

//       return;
//     }

//     // ALL OTHER INSURANCE TYPES: retain the existing generic API.
//     const applicant = { ...this.a };

//     const payload = {
//       insuranceType: code,
//       applicant,
//       fields: this.values,
//       ...(!this.token
//         ? { verificationToken: this.verificationToken() ?? undefined }
//         : {})
//     };

//     this.api.submitForm(code, this.token, payload).subscribe({
//       next: r => {
//         this.result.set(r);
//         this.busy.set(false);
//       },
//       error: e => {
//         this.error.set(errorMessage(e));
//         this.busy.set(false);
//       }
//     });
//   }

//   private fail(e: unknown, fallback?: string) {
//     this.error.set(errorMessage(e, fallback));
//     this.loading.set(false);
//   }
// }







import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
 
import {
  ApiService,
  ApplicantPayload,
  FormLink,
  PublicCountry,
  PublicForm,
  PublicIdentityType,
  PublicInsuranceType,
  SubmissionResult
} from '../core/api.service';

import { errorMessage } from '../core/http-error';
 
const PERSONAL = [
  'name',
  'email',
  'phone',
  'dateOfBirth',
  'gender',
  'nationality',
  'countryOfResidence',
  'identityCountry',
  'identityType',
  'identityNumber'
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
 
  // The application form remains locked until phone verification succeeds.
  unlocked = signal(false);
 
  // New-customer OTP challenge and verification token.
  challengeId = signal<string | null>(null);
  verificationToken = signal<string | null>(null);

  // Tracks whether existing customer details were loaded.
  private loadedExistingClient = false;
 
  token: string | null = null;

  // Selected international calling code.
  phoneCallingCode = '+91';

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
      f =>
        !(PERSONAL as readonly string[]).includes(f.fieldName) &&
        !(this.type() === 'TRAVEL' && f.fieldName === 'travellers')
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

    // Load countries and international calling codes.
    this.api.publicCountries().subscribe({
      next: countries => {
        this.countries.set(countries);

        // Set India's calling code as the default when available.
        const india = countries.find(
          country => country.code.toUpperCase() === 'IN'
        );

        if (india?.callingCode) {
          this.phoneCallingCode = india.callingCode;
        }

        // Synchronize saved country names/codes after countries load.
        this.syncCountrySelections();

        if (this.a.identityCountry) {
          this.refreshIdTypes();
        }
      },
      error: e => {
        console.error('Unable to load countries:', e);
      }
    });
 
    if (this.token) {
      this.api.formLink(this.token).subscribe({
        next: link => {
          this.link.set(link);
          this.applyPrefill(link);
          this.loadForm(link.insuranceType);
        },
        error: e =>
          this.fail(e, 'This link is not valid or has expired.')
      });
    } else if (routeType) {
      this.loadForm(routeType);
    } else {
      this.api.publicInsuranceTypes().subscribe({
        next: types => {
          this.types.set(types);
          this.loading.set(false);
        },
        error: e => this.fail(e)
      });
    }
  }
 
  /**
   * Returns the calling code for a country.
   */
  getCallingCode(country: PublicCountry): string {
    return String(country.callingCode ?? '').trim();
  }

  /**
   * Combines the selected calling code and entered phone number.
   *
   * Example:
   * Calling code: +91
   * Local number: 9875678762
   * Result: +919875678762
   */
  getInternationalPhone(): string {
    const enteredPhone = String(this.a.phone ?? '').trim();

    if (!enteredPhone) {
      return '';
    }

    // Preserve a number that the user has already entered internationally.
    if (enteredPhone.startsWith('+')) {
      const digits = enteredPhone.replace(/\D/g, '');
      return digits ? `+${digits}` : '';
    }

    const localDigits = enteredPhone.replace(/\D/g, '');
    const callingCodeDigits = this.phoneCallingCode.replace(/\D/g, '');

    if (!localDigits || !callingCodeDigits) {
      return '';
    }

    // Remove a leading national trunk zero.
    const nationalNumber = localDigits.replace(/^0+/, '');

    if (!nationalNumber) {
      return '';
    }

    return `+${callingCodeDigits}${nationalNumber}`;
  }

  /**
   * Called when the calling-code dropdown changes.
   */
  onPhoneCallingCodeChange() {
    this.onNewPhoneChange();
  }

  /**
   * Converts a saved country name or ISO code into the ISO code
   * used by the country dropdown.
   *
   * Examples:
   * India -> IN
   * india -> IN
   * IN -> IN
   */
  private normalizeCountryCode(value: string | null | undefined): string | null {
    if (value === null || value === undefined || String(value).trim() === '') {
      return null;
    }

    const countryValue = String(value).trim().toLowerCase();

    const matchedCountry = this.countries().find(country => {
      const countryCode = String(country.code ?? '').trim().toLowerCase();
      const countryName = String(country.name ?? '').trim().toLowerCase();

      return (
        countryCode === countryValue ||
        countryName === countryValue
      );
    });

    return matchedCountry?.code ?? null;
  }

  /**
   * Synchronizes both country dropdowns with saved customer details.
   */
  private syncCountrySelections() {
    const residenceCode = this.normalizeCountryCode(
      this.a.countryOfResidence
    );

    if (residenceCode) {
      this.a.countryOfResidence = residenceCode;
    }

    const identityCode = this.normalizeCountryCode(
      this.a.identityCountry
    );

    if (identityCode) {
      this.a.identityCountry = identityCode;
    }
  }
 
  pick(code: string) {
    this.unlocked.set(false);
    this.otpSent.set(false);
    this.otpMsg.set('');
    this.otpCode = '';

    this.challengeId.set(null);
    this.verificationToken.set(null);

    this.loadedExistingClient = false;
 
    this.router.navigate(['/form', code]);

    this.loading.set(true);
    this.loadForm(code);
  }
 
  private loadForm(code: string) {
    this.type.set(code);
 
    this.api.publicForm(code).subscribe({
      next: form => {
        this.form.set(form);

        // Default travel group size to one.
        if (
          code.toUpperCase() === 'TRAVEL' &&
          (
            this.values['travellers'] === undefined ||
            String(this.values['travellers']).trim() === ''
          )
        ) {
          this.values['travellers'] = '1';
        }
 
        this.loading.set(false);
        this.refreshIdTypes();
      },
      error: e => this.fail(e)
    });
  }

  /**
   * Applies details received through an existing form link.
   */
  private applyPrefill(link: FormLink) {
    const client = link.client;

    if (client) {
      this.a.name = client.name ?? null;
      this.a.email = client.email ?? null;
      this.a.dateOfBirth = client.dateOfBirth ?? null;
      this.a.gender = client.gender ?? null;
      this.a.nationality = client.nationality ?? null;
      this.a.countryOfResidence = client.countryOfResidence ?? null;

      this.a.identityCountry = client.identityCountry ?? null;
      this.a.identityType = client.identityType ?? null;

      // Never put a masked phone number into the actual phone field.
      this.a.phone = null;
    }

    for (const [key, value] of Object.entries(link.prefill ?? {})) {
      if (link.existingClient && key === 'phone') {
        continue;
      }

      if ((PERSONAL as readonly string[]).includes(key)) {
        (this.a as unknown as Record<string, string | null>)[key] = value;
      } else {
        this.values[key] = value;
      }
    }

    this.syncCountrySelections();
  }

  /**
   * Reset OTP verification when the new-customer phone changes.
   */
  onNewPhoneChange() {
    // Keep the original form-link OTP flow unchanged.
    if (this.token) {
      return;
    }

    this.otpSent.set(false);
    this.otpMsg.set('');
    this.otpCode = '';

    this.challengeId.set(null);
    this.verificationToken.set(null);

    this.unlocked.set(false);

    // Clear previously loaded customer details when the phone changes.
    if (this.loadedExistingClient) {
      this.a.name = null;
      this.a.email = null;
      this.a.dateOfBirth = null;
      this.a.gender = null;
      this.a.nationality = null;
      this.a.countryOfResidence = null;
      this.a.identityCountry = null;
      this.a.identityType = null;
      this.a.identityNumber = null;

      this.loadedExistingClient = false;

      this.refreshIdTypes();
    }
  }

  /**
   * Sends OTP to the selected phone number.
   */
  sendOtp() {
    this.busy.set(true);
    this.otpMsg.set('');
    this.error.set('');

    // Existing form-link customer: retain the original OTP flow.
    if (this.token) {
      this.api.sendOtp(this.token).subscribe({
        next: response => {
          this.otpSent.set(true);

          this.otpMsg.set(
            'Verification code sent to ' +
            response.phoneMasked +
            '.'
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

    // New or existing customer: send the complete international number.
    const phone = this.getInternationalPhone();
    const phoneDigits = phone.replace(/\D/g, '');

    if (
      !phone ||
      phoneDigits.length < 7 ||
      phoneDigits.length > 15
    ) {
      this.otpMsg.set('Please enter a valid mobile number.');
      this.busy.set(false);
      return;
    }
 
    this.api.sendNewCustomerOtp(phone).subscribe({
      next: response => {
        this.challengeId.set(response.challengeId);
        this.otpSent.set(true);

        this.otpMsg.set(
          'Verification code sent to ' +
          response.phoneMasked +
          '.'
        );

        this.busy.set(false);
      },
      error: e => {
        this.otpMsg.set(errorMessage(e));
        this.busy.set(false);
      }
    });
  }

  /**
   * Verifies OTP and loads saved details for existing customers.
   */
  verifyOtp() {
    const code = this.otpCode.trim();
 
    if (!/^\d{6}$/.test(code)) {
      this.otpMsg.set('Please enter the 6-digit verification code.');
      return;
    }
 
    this.busy.set(true);
    this.otpMsg.set('');

    // Existing form-link customer: retain the original OTP flow.
    if (this.token) {
      this.api.verifyOtp(this.token, code).subscribe({
        next: response => {
          this.busy.set(false);

          if (response.verified) {
            this.unlocked.set(true);
            this.otpMsg.set('Phone verified successfully.');
          } else {
            this.otpMsg.set(
              response.message || 'Incorrect verification code.'
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

    // New or existing customer OTP verification.
    const challenge = this.challengeId();
 
    if (!challenge) {
      this.otpMsg.set('Please request a new verification code.');
      this.busy.set(false);
      return;
    }
 
    this.api.verifyNewCustomerOtp(challenge, code).subscribe({
      next: response => {
        this.busy.set(false);

        if (
          !response.verified ||
          !response.verificationToken
        ) {
          this.otpMsg.set(
            response.message || 'Incorrect verification code.'
          );
          return;
        }

        this.verificationToken.set(response.verificationToken);
        this.unlocked.set(true);

        // Existing customer: populate saved personal details.
        if (response.existingClient && response.client) {
          const client = response.client;

          this.a.name = client.name ?? null;
          this.a.email = client.email ?? null;

          // Keep the phone number entered and verified by OTP.
          // Do not replace it with a masked or saved phone number.

          this.a.dateOfBirth = client.dateOfBirth ?? null;
          this.a.gender = client.gender ?? null;
          this.a.nationality = client.nationality ?? null;

          this.a.countryOfResidence =
            client.countryOfResidence ?? null;

          this.a.identityCountry =
            client.identityCountry ?? null;

          this.a.identityType =
            client.identityType ?? null;

          // A masked identity number is not the actual identity number.
          this.a.identityNumber = null;

          // Match country names and ISO codes to dropdown values.
          this.syncCountrySelections();

          this.loadedExistingClient = true;

          // Load identity types for the saved issuing country.
          this.refreshIdTypes();
 
          this.otpMsg.set(
            'Phone verified successfully. Your existing personal details have been loaded.'
          );
        } else if (response.ambiguousPhone) {
          this.otpMsg.set(
            'Phone verified, but multiple customer records match this number. Please contact the support team.'
          );
        } else {
          // New customer: leave personal details available for manual entry.
          this.loadedExistingClient = false;

          this.otpMsg.set(
            'Phone verified successfully. Please enter your personal details.'
          );
        }
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
      next: types => this.idTypes.set(types),
      error: () => this.idTypes.set([])
    });
  }

  inputType(type: string) {
    return type === 'DATE'
      ? 'date'
      : type === 'NUMBER'
        ? 'number'
        : type === 'EMAIL'
          ? 'email'
          : 'text';
  }

  /**
   * Submits the insurance application.
   */
  submit() {
    const code = this.type();

    if (!code) {
      return;
    }

    if (!this.unlocked()) {
      this.error.set(
        'Please verify your phone number before submitting.'
      );
      return;
    }
 
    this.busy.set(true);
    this.error.set('');

    // Use international phone for new customers.
    // Preserve the original phone handling for form-link customers.
    const submittedPhone = this.token
      ? this.a.phone
      : this.getInternationalPhone();

    if (!submittedPhone) {
      this.error.set('Please enter a valid mobile number.');
      this.busy.set(false);
      return;
    }

    // TRAVEL INSURANCE.
    if (code.toUpperCase() === 'TRAVEL') {
      const rawTravellerCount = this.values['travellers'];
 
      const travellerCount = Number(
        rawTravellerCount === undefined ||
        rawTravellerCount === null ||
        String(rawTravellerCount).trim() === ''
          ? 1
          : rawTravellerCount
      );
 
      if (
        !Number.isInteger(travellerCount) ||
        travellerCount < 1 ||
        travellerCount > 20
      ) {
        this.error.set(
          'Please enter a valid number of travellers (1-20).'
        );

        this.busy.set(false);
        return;
      }
 
      const travelApplicant = {
        name: this.a.name,
        email: this.a.email,
        phone: submittedPhone,
        dateOfBirth: this.a.dateOfBirth,
        gender: this.a.gender,
        nationality: this.a.nationality,
        countryOfResidence: this.a.countryOfResidence,
        passportNumber: this.values['passportNumber']
      };
 
      const travelPayload = {
        applicant: travelApplicant,
        additionalTravellers: [],
        travellers: travellerCount,
        departureCountry: this.values['originCountry'],
        destinationCountry: this.values['destinationCountry'],
        travelStartDate: this.values['travelStartDate'],
        travelEndDate: this.values['travelEndDate'],
        purposeOfTravel: this.values['purposeOfTravel'],

        ...(!this.token
          ? {
              verificationToken:
                this.verificationToken() ?? undefined
            }
          : {})
      };

      this.api.submitQuotationForm(
        travelPayload,
        this.token
      ).subscribe({
        next: response => {
          this.result.set(response);
          this.busy.set(false);
        },
        error: e => {
          this.error.set(errorMessage(e));
          this.busy.set(false);
        }
      });
 
      return;
    }

    // ALL OTHER INSURANCE TYPES.
    const applicant = {
      ...this.a,
      phone: submittedPhone
    };

    const payload = {
      insuranceType: code,
      applicant,
      fields: this.values,

      ...(!this.token
        ? {
            verificationToken:
              this.verificationToken() ?? undefined
          }
        : {})
    };

    this.api.submitForm(
      code,
      this.token,
      payload
    ).subscribe({
      next: response => {
        this.result.set(response);
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
 
 