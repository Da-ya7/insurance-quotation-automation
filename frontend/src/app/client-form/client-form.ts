// import { Component, computed, inject, signal } from '@angular/core';
// import { FormsModule } from '@angular/forms';
// import { ActivatedRoute, Router } from '@angular/router';

// import {
//   ApiService,
//   ApplicantPayload,
//   FormLink,
//   FormLinkClient,
//   PublicCountry,
//   PublicForm,
//   PublicIdentityType,
//   PublicInsuranceType,
//   SubmissionResult
// } from '../core/api.service';

// import { errorMessage } from '../core/http-error';

// const PERSONAL = [
//   'name',
//   'email',
//   'phone',
//   'dateOfBirth',
//   'gender',
//   'nationality',
//   'countryOfResidence',
//   'identityCountry',
//   'identityType',
//   'identityNumber'
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
//   identityMatchTypes = signal<PublicIdentityType[]>([]);

//   form = signal<PublicForm | null>(null);
//   link = signal<FormLink | null>(null);
//   type = signal<string | null>(null);

//   // Phone OTP
//   otpSent = signal(false);
//   otpMsg = signal('');
//   otpCode = '';

//   // Personal form remains locked until verification is complete.
//   unlocked = signal(false);

//   challengeId = signal<string | null>(null);
//   verificationToken = signal<string | null>(null);

//   // Additional identity verification.
//   identityCheckRequired = signal(false);

//   identityMatchCountry = '';
//   identityMatchType = '';
//   identityMatchNumber = '';
//   identityMatchMsg = '';

//   private loadedExistingClient = false;

//   token: string | null = null;

//   phoneCallingCode = '+91';

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

//   otherFields = computed(() =>
//     (this.form()?.fields ?? []).filter(
//       f =>
//         !(PERSONAL as readonly string[]).includes(f.fieldName) &&
//         !(this.type() === 'TRAVEL' && f.fieldName === 'travellers')
//     )
//   );

//   idExample = computed(() =>
//     this.idTypes().find(
//       t => t.code === this.a.identityType
//     )?.example ?? null
//   );

//   constructor() {
//     const qp = this.route.snapshot.queryParamMap;

//     this.token = qp.get('token');

//     const routeType =
//       this.route.snapshot.paramMap.get('type') ?? qp.get('type');

//     this.api.publicCountries().subscribe({
//       next: countries => {
//         this.countries.set(countries);

//         const india = countries.find(
//           country => country.code.toUpperCase() === 'IN'
//         );

//         if (india?.callingCode) {
//           this.phoneCallingCode = india.callingCode;
//         }

//         this.syncCountrySelections();

//         if (this.a.identityCountry) {
//           this.refreshIdTypes();
//         }
//       },
//       error: e => {
//         console.error('Unable to load countries:', e);
//       }
//     });

//     // Existing email form link.
//     if (this.token) {
//       this.api.formLink(this.token).subscribe({
//         next: link => {
//           this.link.set(link);
//           this.applyPrefill(link);
//           this.loadForm(link.insuranceType);
//         },
//         error: e =>
//           this.fail(e, 'This link is not valid or has expired.')
//       });
//     } else if (routeType) {
//       this.loadForm(routeType);
//     } else {
//       this.api.publicInsuranceTypes().subscribe({
//         next: types => {
//           this.types.set(types);
//           this.loading.set(false);
//         },
//         error: e => this.fail(e)
//       });
//     }
//   }

//   // ------------------------------------------------------------
//   // Phone handling
//   // ------------------------------------------------------------

//   getCallingCode(country: PublicCountry): string {
//     return String(country.callingCode ?? '').trim();
//   }

//   getInternationalPhone(): string {
//     const enteredPhone = String(this.a.phone ?? '').trim();

//     if (!enteredPhone) {
//       return '';
//     }

//     if (enteredPhone.startsWith('+')) {
//       const digits = enteredPhone.replace(/\D/g, '');
//       return digits ? `+${digits}` : '';
//     }

//     const localDigits = enteredPhone.replace(/\D/g, '');
//     const callingCodeDigits = this.phoneCallingCode.replace(/\D/g, '');

//     if (!localDigits || !callingCodeDigits) {
//       return '';
//     }

//     const nationalNumber = localDigits.replace(/^0+/, '');

//     if (!nationalNumber) {
//       return '';
//     }

//     return `+${callingCodeDigits}${nationalNumber}`;
//   }

//   onPhoneCallingCodeChange() {
//     this.onNewPhoneChange();
//   }

//   private normalizeCountryCode(
//     value: string | null | undefined
//   ): string | null {
//     if (
//       value === null ||
//       value === undefined ||
//       String(value).trim() === ''
//     ) {
//       return null;
//     }

//     const countryValue = String(value).trim().toLowerCase();

//     const matchedCountry = this.countries().find(country => {
//       const countryCode = String(country.code ?? '').trim().toLowerCase();
//       const countryName = String(country.name ?? '').trim().toLowerCase();

//       return (
//         countryCode === countryValue ||
//         countryName === countryValue
//       );
//     });

//     return matchedCountry?.code ?? null;
//   }

//   private syncCountrySelections() {
//     const residenceCode = this.normalizeCountryCode(
//       this.a.countryOfResidence
//     );

//     if (residenceCode) {
//       this.a.countryOfResidence = residenceCode;
//     }

//     const identityCode = this.normalizeCountryCode(
//       this.a.identityCountry
//     );

//     if (identityCode) {
//       this.a.identityCountry = identityCode;
//     }
//   }

//   onNewPhoneChange() {
//     // Do not change the existing email form-link OTP flow.
//     if (this.token) {
//       return;
//     }

//     this.otpSent.set(false);
//     this.otpMsg.set('');
//     this.otpCode = '';

//     this.challengeId.set(null);
//     this.verificationToken.set(null);

//     this.unlocked.set(false);
//     this.resetIdentityCheck();

//     // Clear identity information for the changed phone.
//     this.a.identityCountry = null;
//     this.a.identityType = null;
//     this.a.identityNumber = null;

//     // Clear previously loaded customer details.
//     if (this.loadedExistingClient) {
//       this.a.name = null;
//       this.a.email = null;
//       this.a.dateOfBirth = null;
//       this.a.gender = null;
//       this.a.nationality = null;
//       this.a.countryOfResidence = null;

//       this.loadedExistingClient = false;
//     }

//     this.refreshIdTypes();
//   }

//   // ------------------------------------------------------------
//   // Insurance form
//   // ------------------------------------------------------------

//   pick(code: string) {
//     this.unlocked.set(false);
//     this.otpSent.set(false);
//     this.otpMsg.set('');
//     this.otpCode = '';

//     this.challengeId.set(null);
//     this.verificationToken.set(null);

//     this.resetIdentityCheck();
//     this.loadedExistingClient = false;

//     this.router.navigate(['/form', code]);

//     this.loading.set(true);
//     this.loadForm(code);
//   }

//   private loadForm(code: string) {
//     this.type.set(code);

//     this.api.publicForm(code).subscribe({
//       next: form => {
//         this.form.set(form);

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

//   private applyPrefill(link: FormLink) {
//     const client = link.client;

//     if (client) {
//       this.a.name = client.name ?? null;
//       this.a.email = client.email ?? null;
//       this.a.dateOfBirth = client.dateOfBirth ?? null;
//       this.a.gender = client.gender ?? null;
//       this.a.nationality = client.nationality ?? null;
//       this.a.countryOfResidence = client.countryOfResidence ?? null;

//       this.a.identityCountry = client.identityCountry ?? null;
//       this.a.identityType = client.identityType ?? null;

//       // Never put a masked phone number into the actual phone field.
//       this.a.phone = null;
//     }

//     for (const [key, value] of Object.entries(link.prefill ?? {})) {
//       if (link.existingClient && key === 'phone') {
//         continue;
//       }

//       if ((PERSONAL as readonly string[]).includes(key)) {
//         (this.a as unknown as Record<string, string | null>)[key] = value;
//       } else {
//         this.values[key] = value;
//       }
//     }

//     this.syncCountrySelections();
//   }

//   // ------------------------------------------------------------
//   // Reset additional identity verification
//   // ------------------------------------------------------------

//   private resetIdentityCheck() {
//     this.identityCheckRequired.set(false);

//     this.identityMatchCountry = '';
//     this.identityMatchType = '';
//     this.identityMatchNumber = '';
//     this.identityMatchMsg = '';

//     this.identityMatchTypes.set([]);
//   }

//   // ------------------------------------------------------------
//   // Send phone OTP
//   // ------------------------------------------------------------

//   sendOtp() {
//     this.busy.set(true);
//     this.otpMsg.set('');
//     this.error.set('');

//     // Existing form-link OTP behavior.
//     if (this.token) {
//       this.api.sendOtp(this.token).subscribe({
//         next: response => {
//           this.otpSent.set(true);

//           this.otpMsg.set(
//             'Verification code sent to ' +
//             response.phoneMasked +
//             '.'
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

//     const phone = this.getInternationalPhone();
//     const phoneDigits = phone.replace(/\D/g, '');

//     if (
//       !phone ||
//       phoneDigits.length < 7 ||
//       phoneDigits.length > 15
//     ) {
//       this.otpMsg.set('Please enter a valid mobile number.');
//       this.busy.set(false);
//       return;
//     }

//     this.api.sendNewCustomerOtp(phone).subscribe({
//       next: response => {
//         this.challengeId.set(response.challengeId);
//         this.otpSent.set(true);

//         this.otpMsg.set(
//           'Verification code sent to ' +
//           response.phoneMasked +
//           '.'
//         );

//         this.busy.set(false);
//       },
//       error: e => {
//         this.otpMsg.set(errorMessage(e));
//         this.busy.set(false);
//       }
//     });
//   }

//   // ------------------------------------------------------------
//   // Verify phone OTP
//   // ------------------------------------------------------------

//   verifyOtp() {
//     const code = this.otpCode.trim();

//     if (!/^\d{6}$/.test(code)) {
//       this.otpMsg.set('Please enter the 6-digit verification code.');
//       return;
//     }

//     this.busy.set(true);
//     this.otpMsg.set('');

//     // Existing email form-link customer.
//     if (this.token) {
//       this.api.verifyOtp(this.token, code).subscribe({
//         next: response => {
//           this.busy.set(false);

//           if (response.verified) {
//             this.unlocked.set(true);
//             this.otpMsg.set('Phone verified successfully.');
//           } else {
//             this.otpMsg.set(
//               response.message || 'Incorrect verification code.'
//             );
//           }
//         },
//         error: e => {
//           this.otpMsg.set(errorMessage(e));
//           this.busy.set(false);
//         }
//       });

//       return;
//     }

//     const challenge = this.challengeId();

//     if (!challenge) {
//       this.otpMsg.set('Please request a new verification code.');
//       this.busy.set(false);
//       return;
//     }

//     this.api.verifyNewCustomerOtp(challenge, code).subscribe({
//       next: response => {
//         this.busy.set(false);

//         // OTP verification must succeed first.
//         if (
//           !response.verified ||
//           !response.verificationToken
//         ) {
//           this.otpMsg.set(
//             response.message || 'Incorrect verification code.'
//           );
//           return;
//         }

//         this.verificationToken.set(response.verificationToken);

//         // Safe debugging: do not log phone numbers, identity numbers,
//         // or verification tokens.
//         console.log('[OTP DEBUG] Verification result:', {
//           verified: response.verified,
//           existingClient: response.existingClient,
//           hasClient: !!response.client,
//           requiresIdentityCheck: response.requiresIdentityCheck,
//           newCustomerAllowed: response.newCustomerAllowed,
//           clientNameAvailable: !!response.client?.name
//         });

//         // CASE 1:
//         // Multiple clients share this phone.
//         // Identity verification is required before loading a client.
//         if (response.requiresIdentityCheck) {
//           this.unlocked.set(false);
//           this.identityCheckRequired.set(true);

//           this.identityMatchCountry = '';
//           this.identityMatchType = '';
//           this.identityMatchNumber = '';
//           this.identityMatchMsg = '';

//           this.identityMatchTypes.set([]);

//           this.otpMsg.set(
//             'Phone verified. Multiple customers are registered with this number. Please complete the additional identity verification.'
//           );

//           return;
//         }

//         // CASE 2:
//         // A client was returned and additional identity verification
//         // is NOT required. Load the client even if the backend's
//         // existingClient flag is false or missing.
//         if (response.client) {
//           this.loadExistingClient(response.client);

//           this.identityCheckRequired.set(false);
//           this.unlocked.set(true);

//           this.otpMsg.set(
//             'Phone verified successfully. Your existing customer details have been loaded.'
//           );

//           return;
//         }

//         // CASE 3:
//         // No existing customer has this phone.
//         if (response.newCustomerAllowed) {
//           this.loadedExistingClient = false;
//           this.identityCheckRequired.set(false);
//           this.unlocked.set(true);

//           this.otpMsg.set(
//             'Phone verified successfully. Please enter your personal details.'
//           );

//           return;
//         }

//         // Unexpected backend response.
//         this.unlocked.set(false);

//         this.otpMsg.set(
//           response.message ||
//           'Unable to identify the customer. Please try again.'
//         );
//       },
//       error: e => {
//         this.otpMsg.set(errorMessage(e));
//         this.busy.set(false);
//       }
//     });
//   }

//   // ------------------------------------------------------------
//   // Load identity types based on selected country
//   // ------------------------------------------------------------

//   onIdentityMatchCountry(code: string | null) {
//     this.identityMatchCountry = code ?? '';
//     this.identityMatchType = '';
//     this.identityMatchTypes.set([]);
//     this.identityMatchMsg = '';

//     if (!this.identityMatchCountry) {
//       return;
//     }

//     this.api.publicIdentityTypes(this.identityMatchCountry).subscribe({
//       next: types => this.identityMatchTypes.set(types),
//       error: () => {
//         this.identityMatchTypes.set([]);
//         this.identityMatchMsg =
//           'Unable to load identity types. Please try again.';
//       }
//     });
//   }

//   // ------------------------------------------------------------
//   // Additional identity verification
//   // ------------------------------------------------------------

//   verifyIdentity() {
//     const identityNumber = this.identityMatchNumber.trim();
//     const identityCountry = this.identityMatchCountry.trim();
//     const identityType = this.identityMatchType.trim();
//     const verificationToken = this.verificationToken();

//     this.identityMatchMsg = '';

//     if (!verificationToken) {
//       this.identityMatchMsg =
//         'Your phone verification has expired. Please verify your phone again.';
//       return;
//     }

//     if (!identityCountry) {
//       this.identityMatchMsg = 'Please select your identity country.';
//       return;
//     }

//     if (!identityType) {
//       this.identityMatchMsg = 'Please select your identity type.';
//       return;
//     }

//     if (!identityNumber) {
//       this.identityMatchMsg = 'Please enter your identity number.';
//       return;
//     }

//     this.busy.set(true);

//     this.api.verifyNewCustomerIdentity(
//       verificationToken,
//       identityCountry,
//       identityType,
//       identityNumber
//     ).subscribe({
//       next: response => {
//         this.busy.set(false);

//         // Identity belongs to a customer using another phone number.
//         if (response.duplicateIdentity) {
//           this.unlocked.set(false);

//           this.identityMatchMsg =
//             response.message ||
//             'This identity number is already registered to another mobile number. Please contact support.';

//           return;
//         }

//         // Identity matches a customer using this verified phone.
//         if (response.matched && response.client) {
//           this.loadExistingClient(response.client);

//           // Retain the identity just verified by the customer.
//           this.a.identityCountry = identityCountry;
//           this.a.identityType = identityType;
//           this.a.identityNumber = identityNumber;

//           this.identityCheckRequired.set(false);

//           this.identityMatchNumber = '';
//           this.identityMatchMsg = '';

//           this.unlocked.set(true);

//           this.otpMsg.set(
//             'Identity matched successfully. Your existing personal details have been loaded.'
//           );

//           return;
//         }

//         // Identity is not registered anywhere.
//         // Allow this person to register as a new customer.
//         if (response.newCustomerAllowed) {
//           this.loadedExistingClient = false;

//           this.a.identityCountry = identityCountry;
//           this.a.identityType = identityType;
//           this.a.identityNumber = identityNumber;

//           this.syncCountrySelections();
//           this.refreshIdTypes();

//           this.identityCheckRequired.set(false);

//           this.identityMatchNumber = '';
//           this.identityMatchMsg = '';

//           this.unlocked.set(true);

//           this.otpMsg.set(
//             'Identity verification completed. You can continue as a new customer.'
//           );

//           return;
//         }

//         // No valid result from backend.
//         this.unlocked.set(false);

//         this.identityMatchMsg =
//           response.message ||
//           'The identity number could not be verified. Please check your details.';
//       },
//       error: e => {
//         this.busy.set(false);
//         this.unlocked.set(false);
//         this.identityMatchMsg = errorMessage(e);
//       }
//     });
//   }

//   // ------------------------------------------------------------
//   // Load the exact matched customer's personal details
//   // ------------------------------------------------------------

//   private loadExistingClient(client: FormLinkClient) {
//     this.a.name = client.name ?? null;
//     this.a.email = client.email ?? null;

//     // Keep the phone number entered and OTP-verified by the customer.
//     // Do not replace it with a masked phone number.
//     this.a.dateOfBirth = client.dateOfBirth ?? null;
//     this.a.gender = client.gender ?? null;
//     this.a.nationality = client.nationality ?? null;
//     this.a.countryOfResidence = client.countryOfResidence ?? null;

//     this.a.identityCountry = client.identityCountry ?? null;
//     this.a.identityType = client.identityType ?? null;

//     // Never expose the stored full identity number.
//     // In the multiple-client case, verifyIdentity() assigns
//     // the identity number just verified by the customer.
//     this.a.identityNumber = null;

//     this.syncCountrySelections();

//     this.loadedExistingClient = true;

//     this.refreshIdTypes();
//   }

//   // ------------------------------------------------------------
//   // Main form identity dropdowns
//   // ------------------------------------------------------------

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
//       next: types => this.idTypes.set(types),
//       error: () => this.idTypes.set([])
//     });
//   }

//   inputType(type: string) {
//     return type === 'DATE'
//       ? 'date'
//       : type === 'NUMBER'
//         ? 'number'
//         : type === 'EMAIL'
//           ? 'email'
//           : 'text';
//   }

//   // ------------------------------------------------------------
//   // Submit quotation form
//   // ------------------------------------------------------------

//   submit() {
//     const code = this.type();

//     if (!code) {
//       return;
//     }

//     if (!this.unlocked()) {
//       this.error.set(
//         'Please complete phone verification before submitting.'
//       );
//       return;
//     }

//     if (this.identityCheckRequired()) {
//       this.error.set(
//         'Please complete the additional identity verification before submitting.'
//       );
//       return;
//     }

//     this.busy.set(true);
//     this.error.set('');

//     const submittedPhone = this.token
//       ? this.a.phone
//       : this.getInternationalPhone();

//     if (!submittedPhone) {
//       this.error.set('Please enter a valid mobile number.');
//       this.busy.set(false);
//       return;
//     }

//     // ----------------------------------------------------------
//     // TRAVEL INSURANCE
//     // ----------------------------------------------------------

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
//         this.error.set(
//           'Please enter a valid number of travellers (1-20).'
//         );

//         this.busy.set(false);
//         return;
//       }

//       const travelApplicant = {
//         name: this.a.name,
//         email: this.a.email,
//         phone: submittedPhone,
//         dateOfBirth: this.a.dateOfBirth,
//         gender: this.a.gender,
//         nationality: this.a.nationality,
//         countryOfResidence: this.a.countryOfResidence,
//         identityCountry: this.a.identityCountry,
//         identityType: this.a.identityType,
//         identityNumber: this.a.identityNumber,
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
//           ? {
//               verificationToken:
//                 this.verificationToken() ?? undefined
//             }
//           : {})
//       };

//       this.api.submitQuotationForm(
//         travelPayload,
//         this.token
//       ).subscribe({
//         next: response => {
//           this.result.set(response);
//           this.busy.set(false);
//         },
//         error: e => {
//           this.error.set(errorMessage(e));
//           this.busy.set(false);
//         }
//       });

//       return;
//     }

//     // ----------------------------------------------------------
//     // OTHER INSURANCE TYPES
//     // ----------------------------------------------------------

//     const applicant = {
//       ...this.a,
//       phone: submittedPhone
//     };

//     const payload = {
//       insuranceType: code,
//       applicant,
//       fields: this.values,

//       ...(!this.token
//         ? {
//             verificationToken:
//               this.verificationToken() ?? undefined
//           }
//           : {})
//     };

//     this.api.submitForm(
//       code,
//       this.token,
//       payload
//     ).subscribe({
//       next: response => {
//         this.result.set(response);
//         this.busy.set(false);
//       },
//       error: e => {
//         this.error.set(errorMessage(e));
//         this.busy.set(false);
//       }
//     });
//   }

//   // ------------------------------------------------------------
//   // Error handling
//   // ------------------------------------------------------------

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
  FormLinkClient,
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
  identityMatchTypes = signal<PublicIdentityType[]>([]);

  form = signal<PublicForm | null>(null);
  link = signal<FormLink | null>(null);
  type = signal<string | null>(null);

  // Phone OTP
  otpSent = signal(false);
  otpMsg = signal('');
  otpCode = '';

  // Personal form remains locked until verification is complete.
  unlocked = signal(false);

  challengeId = signal<string | null>(null);
  verificationToken = signal<string | null>(null);

  // Additional identity verification.
  identityCheckRequired = signal(false);

  identityMatchCountry = '';
  identityMatchType = '';
  identityMatchNumber = '';
  identityMatchMsg = '';

  private loadedExistingClient = false;
 
  token: string | null = null;

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
    this.idTypes().find(
      t => t.code === this.a.identityType
    )?.example ?? null
  );
 
  constructor() {
    const qp = this.route.snapshot.queryParamMap;

    this.token = qp.get('token');
 
    const routeType =
      this.route.snapshot.paramMap.get('type') ?? qp.get('type');

    this.api.publicCountries().subscribe({
      next: countries => {
        this.countries.set(countries);

        const india = countries.find(
          country => country.code.toUpperCase() === 'IN'
        );

        if (india?.callingCode) {
          this.phoneCallingCode = india.callingCode;
        }

        this.syncCountrySelections();

        if (this.a.identityCountry) {
          this.refreshIdTypes();
        }
      },
      error: e => {
        console.error('Unable to load countries:', e);
      }
    });

    // Existing email form link.
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

  // ------------------------------------------------------------
  // Phone handling
  // ------------------------------------------------------------

  getCallingCode(country: PublicCountry): string {
    return String(country.callingCode ?? '').trim();
  }

  // FIX: Do not add the country code twice.
  getInternationalPhone(): string {
    const enteredPhone = String(this.a.phone ?? '').trim();

    if (!enteredPhone) {
      return '';
    }

    // Already in international format.
    if (enteredPhone.startsWith('+')) {
      const digits = enteredPhone.replace(/\D/g, '');
      return digits ? `+${digits}` : '';
    }

    const localDigits = enteredPhone.replace(/\D/g, '');
    const callingCodeDigits =
      this.phoneCallingCode.replace(/\D/g, '');

    if (!localDigits || !callingCodeDigits) {
      return '';
    }

    // Number already includes the selected country code.
    // Example: 918687655164 with calling code +91.
    if (
      localDigits.startsWith(callingCodeDigits) &&
      localDigits.length > 10
    ) {
      return `+${localDigits}`;
    }

    // Otherwise, add the selected country code.
    const nationalNumber = localDigits.replace(/^0+/, '');

    if (!nationalNumber) {
      return '';
    }

    return `+${callingCodeDigits}${nationalNumber}`;
  }

  onPhoneCallingCodeChange() {
    this.onNewPhoneChange();
  }

  private normalizeCountryCode(
    value: string | null | undefined
  ): string | null {
    if (
      value === null ||
      value === undefined ||
      String(value).trim() === ''
    ) {
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

  onNewPhoneChange() {
    // Do not change the existing email form-link OTP flow.
    if (this.token) {
      return;
    }

    this.otpSent.set(false);
    this.otpMsg.set('');
    this.otpCode = '';

    this.challengeId.set(null);
    this.verificationToken.set(null);

    this.unlocked.set(false);
    this.resetIdentityCheck();

    // Clear identity information for the changed phone.
    this.a.identityCountry = null;
    this.a.identityType = null;
    this.a.identityNumber = null;

    // Clear previously loaded customer details.
    if (this.loadedExistingClient) {
      this.a.name = null;
      this.a.email = null;
      this.a.dateOfBirth = null;
      this.a.gender = null;
      this.a.nationality = null;
      this.a.countryOfResidence = null;

      this.loadedExistingClient = false;
    }

    this.refreshIdTypes();
  }

  // ------------------------------------------------------------
  // Insurance form
  // ------------------------------------------------------------

  pick(code: string) {
    this.unlocked.set(false);
    this.otpSent.set(false);
    this.otpMsg.set('');
    this.otpCode = '';

    this.challengeId.set(null);
    this.verificationToken.set(null);

    this.resetIdentityCheck();
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

  // ------------------------------------------------------------
  // Reset additional identity verification
  // ------------------------------------------------------------

  private resetIdentityCheck() {
    this.identityCheckRequired.set(false);

    this.identityMatchCountry = '';
    this.identityMatchType = '';
    this.identityMatchNumber = '';
    this.identityMatchMsg = '';

    this.identityMatchTypes.set([]);
  }

  // ------------------------------------------------------------
  // Send phone OTP
  // ------------------------------------------------------------

  sendOtp() {
    this.busy.set(true);
    this.otpMsg.set('');
    this.error.set('');

    // Existing form-link OTP behavior.
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

  // ------------------------------------------------------------
  // Verify phone OTP
  // ------------------------------------------------------------

  verifyOtp() {
    const code = this.otpCode.trim();
 
    if (!/^\d{6}$/.test(code)) {
      this.otpMsg.set('Please enter the 6-digit verification code.');
      return;
    }
 
    this.busy.set(true);
    this.otpMsg.set('');

    // Existing email form-link customer.
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

        console.log('[OTP DEBUG] Verification result:', {
          verified: response.verified,
          existingClient: response.existingClient,
          hasClient: !!response.client,
          requiresIdentityCheck: response.requiresIdentityCheck,
          newCustomerAllowed: response.newCustomerAllowed,
          clientNameAvailable: !!response.client?.name
        });

        // CASE 1: Multiple clients share this phone.
        if (response.requiresIdentityCheck) {
          this.unlocked.set(false);
          this.identityCheckRequired.set(true);

          this.identityMatchCountry = '';
          this.identityMatchType = '';
          this.identityMatchNumber = '';
          this.identityMatchMsg = '';

          this.identityMatchTypes.set([]);

          this.otpMsg.set(
            'Phone verified. Multiple customers are registered with this number. Please complete the additional identity verification.'
          );

          return;
        }

        // CASE 2: An existing client was returned.
        if (response.client) {
          this.loadExistingClient(response.client);

          this.identityCheckRequired.set(false);
          this.unlocked.set(true);

          this.otpMsg.set(
            'Phone verified successfully. Your existing customer details have been loaded.'
          );

          return;
        }

        // CASE 3: New customer.
        if (response.newCustomerAllowed) {
          this.loadedExistingClient = false;
          this.identityCheckRequired.set(false);
          this.unlocked.set(true);

          this.otpMsg.set(
            'Phone verified successfully. Please enter your personal details.'
          );

          return;
        }

        // Unexpected backend response.
        this.unlocked.set(false);

        this.otpMsg.set(
          response.message ||
          'Unable to identify the customer. Please try again.'
        );
      },
      error: e => {
        this.otpMsg.set(errorMessage(e));
        this.busy.set(false);
      }
    });
  }

  // ------------------------------------------------------------
  // Load identity types based on selected country
  // ------------------------------------------------------------

  onIdentityMatchCountry(code: string | null) {
    this.identityMatchCountry = code ?? '';
    this.identityMatchType = '';
    this.identityMatchTypes.set([]);
    this.identityMatchMsg = '';

    if (!this.identityMatchCountry) {
      return;
    }

    this.api.publicIdentityTypes(this.identityMatchCountry).subscribe({
      next: types => this.identityMatchTypes.set(types),
      error: () => {
        this.identityMatchTypes.set([]);
        this.identityMatchMsg =
          'Unable to load identity types. Please try again.';
      }
    });
  }

  // ------------------------------------------------------------
  // Additional identity verification
  // ------------------------------------------------------------

  verifyIdentity() {
    const identityNumber = this.identityMatchNumber.trim();
    const identityCountry = this.identityMatchCountry.trim();
    const identityType = this.identityMatchType.trim();
    const verificationToken = this.verificationToken();

    this.identityMatchMsg = '';

    if (!verificationToken) {
      this.identityMatchMsg =
        'Your phone verification has expired. Please verify your phone again.';
      return;
    }

    if (!identityCountry) {
      this.identityMatchMsg = 'Please select your identity country.';
      return;
    }

    if (!identityType) {
      this.identityMatchMsg = 'Please select your identity type.';
      return;
    }

    if (!identityNumber) {
      this.identityMatchMsg = 'Please enter your identity number.';
      return;
    }

    this.busy.set(true);

    this.api.verifyNewCustomerIdentity(
      verificationToken,
      identityCountry,
      identityType,
      identityNumber
    ).subscribe({
      next: response => {
        this.busy.set(false);

        // Identity belongs to a customer using another phone number.
        if (response.duplicateIdentity) {
          this.unlocked.set(false);

          this.identityMatchMsg =
            response.message ||
            'This identity number is already registered to another mobile number. Please contact support.';

          return;
        }

        // Identity matches a customer using this verified phone.
        if (response.matched && response.client) {
          this.loadExistingClient(response.client);

          this.a.identityCountry = identityCountry;
          this.a.identityType = identityType;
          this.a.identityNumber = identityNumber;

          this.identityCheckRequired.set(false);

          this.identityMatchNumber = '';
          this.identityMatchMsg = '';

          this.unlocked.set(true);

          this.otpMsg.set(
            'Identity matched successfully. Your existing personal details have been loaded.'
          );

          return;
        }

        // Identity is not registered anywhere.
        if (response.newCustomerAllowed) {
          this.loadedExistingClient = false;

          this.a.identityCountry = identityCountry;
          this.a.identityType = identityType;
          this.a.identityNumber = identityNumber;

          this.syncCountrySelections();
          this.refreshIdTypes();

          this.identityCheckRequired.set(false);

          this.identityMatchNumber = '';
          this.identityMatchMsg = '';

          this.unlocked.set(true);

          this.otpMsg.set(
            'Identity verification completed. You can continue as a new customer.'
          );

          return;
        }

        // No valid result from backend.
        this.unlocked.set(false);

        this.identityMatchMsg =
          response.message ||
          'The identity number could not be verified. Please check your details.';
      },
      error: e => {
        this.busy.set(false);
        this.unlocked.set(false);
        this.identityMatchMsg = errorMessage(e);
      }
    });
  }

  // ------------------------------------------------------------
  // Load the exact matched customer's personal details
  // ------------------------------------------------------------

  private loadExistingClient(client: FormLinkClient) {
    this.a.name = client.name ?? null;
    this.a.email = client.email ?? null;

    // Keep the phone number entered and OTP-verified by the customer.
    // Do not replace it with a masked phone number.
    this.a.dateOfBirth = client.dateOfBirth ?? null;
    this.a.gender = client.gender ?? null;
    this.a.nationality = client.nationality ?? null;
    this.a.countryOfResidence = client.countryOfResidence ?? null;

    this.a.identityCountry = client.identityCountry ?? null;
    this.a.identityType = client.identityType ?? null;

    // Never expose the stored full identity number.
    this.a.identityNumber = null;

    this.syncCountrySelections();

    this.loadedExistingClient = true;

    this.refreshIdTypes();
  }

  // ------------------------------------------------------------
  // Main form identity dropdowns
  // ------------------------------------------------------------

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

  // ------------------------------------------------------------
  // Submit quotation form
  // ------------------------------------------------------------

  submit() {
    const code = this.type();

    if (!code) {
      return;
    }

    if (!this.unlocked()) {
      this.error.set(
        'Please complete phone verification before submitting.'
      );
      return;
    }

    if (this.identityCheckRequired()) {
      this.error.set(
        'Please complete the additional identity verification before submitting.'
      );
      return;
    }
 
    this.busy.set(true);
    this.error.set('');

    const submittedPhone = this.token
      ? this.a.phone
      : this.getInternationalPhone();

    if (!submittedPhone) {
      this.error.set('Please enter a valid mobile number.');
      this.busy.set(false);
      return;
    }

    // ----------------------------------------------------------
    // TRAVEL INSURANCE
    // ----------------------------------------------------------

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

      const passportCountry = String(
        this.values['passportCountry'] ??
        this.values['passportIssuingCountry'] ??
        ''
      ).trim();

      if (!passportCountry) {
        this.error.set(
          'Please select your passport issuing country.'
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
        identityCountry: this.a.identityCountry,
        identityType: this.a.identityType,
        identityNumber: this.a.identityNumber,
        passportNumber: this.values['passportNumber'],
        passportCountry: passportCountry
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

    // ----------------------------------------------------------
    // OTHER INSURANCE TYPES
    // ----------------------------------------------------------

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

  // ------------------------------------------------------------
  // Error handling
  // ------------------------------------------------------------

  private fail(e: unknown, fallback?: string) {
    this.error.set(errorMessage(e, fallback));
    this.loading.set(false);
  }
}
 
 