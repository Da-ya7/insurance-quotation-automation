
// // import { Component, computed, inject, signal } from '@angular/core';
// // import { FormsModule } from '@angular/forms';
// // import { ActivatedRoute, Router } from '@angular/router';

// // import {
// //   ApiService, ApplicantPayload, FormLink, PublicCountry, PublicForm,
// //   PublicIdentityType, PublicInsuranceType, SubmissionResult
// // } from '../core/api.service';
// // import { errorMessage } from '../core/http-error';

// // const PERSONAL = [
// //   'name', 'email', 'phone', 'dateOfBirth', 'gender', 'nationality',
// //   'countryOfResidence', 'identityCountry', 'identityType', 'identityNumber'
// // ] as const;

// // @Component({
// //   selector: 'app-client-form',
// //   imports: [FormsModule],
// //   templateUrl: './client-form.html',
// //   styleUrl: './client-form.css'
// // })
// // export class ClientForm {
// //   private api = inject(ApiService);
// //   private route = inject(ActivatedRoute);
// //   private router = inject(Router);

// //   loading = signal(true);
// //   busy = signal(false);
// //   error = signal('');
// //   result = signal<SubmissionResult | null>(null);

// //   types = signal<PublicInsuranceType[]>([]);
// //   countries = signal<PublicCountry[]>([]);
// //   idTypes = signal<PublicIdentityType[]>([]);
// //   form = signal<PublicForm | null>(null);
// //   link = signal<FormLink | null>(null);
// //   type = signal<string | null>(null);

// //   otpSent = signal(false);
// //   otpMsg = signal('');
// //   otpCode = '';

// //   // A customer cannot see the application form until OTP verification succeeds.
// //   unlocked = signal(false);

// //   // New-customer OTP challenge and verification token.
// //   challengeId = signal<string | null>(null);
// //   verificationToken = signal<string | null>(null);

// //   token: string | null = null;

// //   a: ApplicantPayload = {
// //     name: null,
// //     email: null,
// //     phone: null,
// //     dateOfBirth: null,
// //     gender: null,
// //     nationality: null,
// //     countryOfResidence: null,
// //     identityCountry: null,
// //     identityType: null,
// //     identityNumber: null
// //   };

// //   values: Record<string, string> = {};

// //   locked = computed(() =>
// //     !!this.link()?.existingClient && !this.unlocked()
// //   );

// //   otherFields = computed(() =>
// //     (this.form()?.fields ?? []).filter(
// //       f => !(PERSONAL as readonly string[]).includes(f.fieldName)
// //     )
// //   );

// //   idExample = computed(() =>
// //     this.idTypes().find(t => t.code === this.a.identityType)?.example ?? null
// //   );

// //   constructor() {
// //     const qp = this.route.snapshot.queryParamMap;
// //     this.token = qp.get('token');

// //     const routeType =
// //       this.route.snapshot.paramMap.get('type') ?? qp.get('type');

// //     this.api.publicCountries().subscribe({
// //       next: c => this.countries.set(c)
// //     });

// //     if (this.token) {
// //       this.api.formLink(this.token).subscribe({
// //         next: l => {
// //           this.link.set(l);
// //           this.applyPrefill(l);
// //           this.loadForm(l.insuranceType);
// //         },
// //         error: e => this.fail(e, 'This link is not valid or has expired.')
// //       });
// //     } else if (routeType) {
// //       this.loadForm(routeType);
// //     } else {
// //       this.api.publicInsuranceTypes().subscribe({
// //         next: t => {
// //           this.types.set(t);
// //           this.loading.set(false);
// //         },
// //         error: e => this.fail(e)
// //       });
// //     }
// //   }

// //   pick(code: string) {
// //     this.unlocked.set(false);
// //     this.otpSent.set(false);
// //     this.otpMsg.set('');
// //     this.otpCode = '';
// //     this.challengeId.set(null);
// //     this.verificationToken.set(null);

// //     this.router.navigate(['/form', code]);
// //     this.loading.set(true);
// //     this.loadForm(code);
// //   }

// //   private loadForm(code: string) {
// //     this.type.set(code);
// //     this.api.publicForm(code).subscribe({
// //       next: f => {
// //         this.form.set(f);
// //         this.loading.set(false);
// //         this.refreshIdTypes();
// //       },
// //       error: e => this.fail(e)
// //     });
// //   }

// //   private applyPrefill(l: FormLink) {
// //     const c = l.client;

// //     if (c) {
// //       this.a.name = c.name;
// //       this.a.email = c.email;
// //       this.a.dateOfBirth = c.dateOfBirth;
// //       this.a.gender = c.gender;
// //       this.a.nationality = c.nationality;
// //       this.a.countryOfResidence = c.countryOfResidence;
// //       this.a.identityCountry = c.identityCountry;
// //       this.a.identityType = c.identityType;

// //       // Never put the masked phone into the actual applicant phone field.
// //       this.a.phone = null;
// //     }

// //     for (const [k, v] of Object.entries(l.prefill ?? {})) {
// //       if (l.existingClient && k === 'phone') {
// //         continue;
// //       }

// //       if ((PERSONAL as readonly string[]).includes(k)) {
// //         (this.a as unknown as Record<string, string | null>)[k] = v;
// //       } else {
// //         this.values[k] = v;
// //       }
// //     }
// //   }

// //   onNewPhoneChange() {
// //     // A newly entered phone number requires a fresh OTP challenge.
// //     if (!this.token) {
// //       this.otpSent.set(false);
// //       this.otpMsg.set('');
// //       this.otpCode = '';
// //       this.challengeId.set(null);
// //       this.verificationToken.set(null);
// //       this.unlocked.set(false);
// //     }
// //   }

// //   sendOtp() {
// //     this.busy.set(true);
// //     this.otpMsg.set('');
// //     this.error.set('');

// //     // Returning customer: OTP goes to the phone associated with the link.
// //     if (this.token) {
// //       this.api.sendOtp(this.token).subscribe({
// //         next: r => {
// //           this.otpSent.set(true);
// //           this.otpMsg.set(
// //             'Verification code sent to ' + r.phoneMasked + '.'
// //           );
// //           this.busy.set(false);
// //         },
// //         error: e => {
// //           this.otpMsg.set(errorMessage(e));
// //           this.busy.set(false);
// //         }
// //       });

// //       return;
// //     }

// //     // New customer: OTP goes to the phone entered on this screen.
// //     const phone = this.a.phone?.trim();

// //     if (!phone) {
// //       this.otpMsg.set('Please enter your mobile number first.');
// //       this.busy.set(false);
// //       return;
// //     }

// //     this.api.sendNewCustomerOtp(phone).subscribe({
// //       next: r => {
// //         this.challengeId.set(r.challengeId);
// //         this.otpSent.set(true);
// //         this.otpMsg.set(
// //           'Verification code sent to ' + r.phoneMasked + '.'
// //         );
// //         this.busy.set(false);
// //       },
// //       error: e => {
// //         this.otpMsg.set(errorMessage(e));
// //         this.busy.set(false);
// //       }
// //     });
// //   }

// //   verifyOtp() {
// //     const code = this.otpCode.trim();

// //     if (!/^\d{6}$/.test(code)) {
// //       this.otpMsg.set('Please enter the 6-digit verification code.');
// //       return;
// //     }

// //     this.busy.set(true);
// //     this.otpMsg.set('');

// //     // Returning customer: verify the personal form-link OTP.
// //     if (this.token) {
// //       this.api.verifyOtp(this.token, code).subscribe({
// //         next: r => {
// //           this.busy.set(false);

// //           if (r.verified) {
// //             this.unlocked.set(true);
// //             this.otpMsg.set('Phone verified successfully.');
// //           } else {
// //             this.otpMsg.set(r.message || 'Incorrect verification code.');
// //           }
// //         },
// //         error: e => {
// //           this.otpMsg.set(errorMessage(e));
// //           this.busy.set(false);
// //         }
// //       });

// //       return;
// //     }

// //     // New customer: verify the phone challenge and receive a submission token.
// //     const challenge = this.challengeId();

// //     if (!challenge) {
// //       this.otpMsg.set('Please request a new verification code.');
// //       this.busy.set(false);
// //       return;
// //     }

// //     this.api.verifyNewCustomerOtp(challenge, code).subscribe({
// //       next: r => {
// //         this.busy.set(false);

// //         if (r.verified && r.verificationToken) {
// //           this.verificationToken.set(r.verificationToken);
// //           this.unlocked.set(true);
// //           this.otpMsg.set('Phone verified successfully.');
// //         } else {
// //           this.otpMsg.set(r.message || 'Incorrect verification code.');
// //         }
// //       },
// //       error: e => {
// //         this.otpMsg.set(errorMessage(e));
// //         this.busy.set(false);
// //       }
// //     });
// //   }

// //   onIdCountry(code: string | null) {
// //     this.a.identityCountry = code;
// //     this.a.identityType = null;
// //     this.refreshIdTypes();
// //   }

// //   private refreshIdTypes() {
// //     if (!this.a.identityCountry) {
// //       this.idTypes.set([]);
// //       return;
// //     }

// //     this.api.publicIdentityTypes(this.a.identityCountry).subscribe({
// //       next: t => this.idTypes.set(t),
// //       error: () => this.idTypes.set([])
// //     });
// //   }

// //   inputType(t: string) {
// //     return t === 'DATE'
// //       ? 'date'
// //       : t === 'NUMBER'
// //         ? 'number'
// //         : t === 'EMAIL'
// //           ? 'email'
// //           : 'text';
// //   }

// //   submit() {
// //     const code = this.type();

// //     if (!code) return;

// //     if (!this.unlocked()) {
// //       this.error.set('Please verify your phone number before submitting.');
// //       return;
// //     }

// //     this.busy.set(true);
// //     this.error.set('');

// //     // TRAVEL INSURANCE: one applicant's details plus the group count.
// //     if (code.toUpperCase() === 'TRAVEL') {
// //       const rawTravellerCount = this.values['travellers'];
// //       const travellerCount = Number(
// //         rawTravellerCount === undefined || rawTravellerCount.trim() === ''
// //           ? 1
// //           : rawTravellerCount
// //       );

// //       if (!Number.isInteger(travellerCount) ||
// //           travellerCount < 1 ||
// //           travellerCount > 20) {
// //         this.error.set('Please enter a valid number of travellers (1-20).');
// //         this.busy.set(false);
// //         return;
// //       }

// //       const travelApplicant = {
// //         name: this.a.name,
// //         email: this.a.email,
// //         phone: this.a.phone,
// //         dateOfBirth: this.a.dateOfBirth,
// //         gender: this.a.gender,
// //         nationality: this.a.nationality,
// //         countryOfResidence: this.a.countryOfResidence,
// //         passportNumber: this.values['passportNumber']
// //       };

// //       const travelPayload = {
// //         applicant: travelApplicant,
// //         additionalTravellers: [],
// //         travellers: travellerCount,
// //         departureCountry: this.values['originCountry'],
// //         destinationCountry: this.values['destinationCountry'],
// //         travelStartDate: this.values['travelStartDate'],
// //         travelEndDate: this.values['travelEndDate'],
// //         purposeOfTravel: this.values['purposeOfTravel'],
// //         ...(!this.token
// //           ? { verificationToken: this.verificationToken() ?? undefined }
// //           : {})
// //       };

// //       this.api.submitQuotationForm(travelPayload, this.token).subscribe({
// //         next: r => {
// //           this.result.set(r);
// //           this.busy.set(false);
// //         },
// //         error: e => {
// //           this.error.set(errorMessage(e));
// //           this.busy.set(false);
// //         }
// //       });

// //       return;
// //     }

// //     // ALL OTHER INSURANCE TYPES: retain the existing generic API.
// //     const applicant = { ...this.a };

// //     const payload = {
// //       insuranceType: code,
// //       applicant,
// //       fields: this.values,
// //       ...(!this.token
// //         ? { verificationToken: this.verificationToken() ?? undefined }
// //         : {})
// //     };

// //     this.api.submitForm(code, this.token, payload).subscribe({
// //       next: r => {
// //         this.result.set(r);
// //         this.busy.set(false);
// //       },
// //       error: e => {
// //         this.error.set(errorMessage(e));
// //         this.busy.set(false);
// //       }
// //     });
// //   }

// //   private fail(e: unknown, fallback?: string) {
// //     this.error.set(errorMessage(e, fallback));
// //     this.loading.set(false);
// //   }
// // }





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

//   // A customer cannot see the application form until OTP verification succeeds.
//   unlocked = signal(false);

//   // New-customer OTP challenge and verification token.
//   challengeId = signal<string | null>(null);
//   verificationToken = signal<string | null>(null);

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
//       next: c => this.countries.set(c)
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

//   pick(code: string) {
//     this.unlocked.set(false);
//     this.otpSent.set(false);
//     this.otpMsg.set('');
//     this.otpCode = '';
//     this.challengeId.set(null);
//     this.verificationToken.set(null);

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
//   }

//   onNewPhoneChange() {
//     // A newly entered phone number requires a fresh OTP challenge.
//     if (!this.token) {
//       this.otpSent.set(false);
//       this.otpMsg.set('');
//       this.otpCode = '';
//       this.challengeId.set(null);
//       this.verificationToken.set(null);
//       this.unlocked.set(false);
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

//     // New customer: OTP goes to the phone entered on this screen.
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

//     // Returning customer: verify the personal form-link OTP.
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

//     // New customer: verify the phone challenge and receive a submission token.
//     const challenge = this.challengeId();

//     if (!challenge) {
//       this.otpMsg.set('Please request a new verification code.');
//       this.busy.set(false);
//       return;
//     }

//     this.api.verifyNewCustomerOtp(challenge, code).subscribe({
//       next: r => {
//         this.busy.set(false);

//         if (r.verified && r.verificationToken) {
//           this.verificationToken.set(r.verificationToken);
//           this.unlocked.set(true);
//           this.otpMsg.set('Phone verified successfully.');
//         } else {
//           this.otpMsg.set(r.message || 'Incorrect verification code.');
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

  // The application form remains locked until phone verification succeeds.
  unlocked = signal(false);

  // New-customer OTP challenge and verification token.
  challengeId = signal<string | null>(null);
  verificationToken = signal<string | null>(null);

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

  // Traveller count is displayed separately for travel insurance.
  // This prevents a duplicate if the backend also returns that field.
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
    this.loadedExistingClient = false;

    this.router.navigate(['/form', code]);
    this.loading.set(true);
    this.loadForm(code);
  }

  private loadForm(code: string) {
    this.type.set(code);

    this.api.publicForm(code).subscribe({
      next: f => {
        this.form.set(f);

        // Default travel group size to one.
        // Preserve any existing value supplied through a form link.
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
    // A different phone number requires a fresh OTP challenge.
    if (!this.token) {
      this.otpSent.set(false);
      this.otpMsg.set('');
      this.otpCode = '';
      this.challengeId.set(null);
      this.verificationToken.set(null);
      this.unlocked.set(false);

      // Do not leave another customer's saved details on screen.
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

    // New or existing customer: OTP goes to the entered phone number.
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

    // Returning customer: preserve the existing form-link OTP flow.
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

    // Verify the phone challenge for a new or existing customer.
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
          this.otpMsg.set(r.message || 'Incorrect verification code.');
          return;
        }

        this.verificationToken.set(r.verificationToken);
        this.unlocked.set(true);

        // Existing customer: automatically load saved personal details.
        if (r.existingClient && r.client) {
          this.a.name = r.client.name;
          this.a.email = r.client.email;

          // Keep the actual phone number entered and verified by OTP.
          this.a.dateOfBirth = r.client.dateOfBirth;
          this.a.gender = r.client.gender;
          this.a.nationality = r.client.nationality;
          this.a.countryOfResidence = r.client.countryOfResidence;
          this.a.identityCountry = r.client.identityCountry;
          this.a.identityType = r.client.identityType;

          // A masked identity number is not the actual identity number.
          this.a.identityNumber = null;

          this.loadedExistingClient = true;
          this.refreshIdTypes();

          this.otpMsg.set(
            'Phone verified successfully. Your existing personal details have been loaded.'
          );
        } else if (r.ambiguousPhone) {
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

    // TRAVEL INSURANCE: one applicant's details plus the group count.
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
        this.error.set('Please enter a valid number of travellers (1-20).');
        this.busy.set(false);
        return;
      }

      const travelApplicant = {
        name: this.a.name,
        email: this.a.email,
        phone: this.a.phone,
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
          ? { verificationToken: this.verificationToken() ?? undefined }
          : {})
      };

      this.api.submitQuotationForm(travelPayload, this.token).subscribe({
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

    // ALL OTHER INSURANCE TYPES: retain the existing generic API.
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
