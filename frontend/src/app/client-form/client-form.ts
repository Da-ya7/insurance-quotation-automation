
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import {
  ApiService,
  ApplicantPayload,
  ClientChoice,
  FormLink,
  FormLinkClient,
  PublicCountry,
  PublicForm,
  PublicIdentityType,
  PublicInsuranceType,
  SelectExistingClientResult,
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
  'countryOfResidence'
] as const;

const IDENTITY_FIELDS = [
  'identityCountry',
  'identityType',
  'identityNumber'
] as const;

const APPLICANT_FIELDS = [
  ...PERSONAL,
  ...IDENTITY_FIELDS
] as const;

const SEARCHABLE_COUNTRY_FIELDS = [
  'identityCountry',
  'passportCountry',
  'passportIssuingCountry',
  'originCountry',
  'destinationCountry'
] as const;

type ConfiguredField = PublicForm['fields'][number] & {
  section?: string;
};

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

  countrySearch: Record<string, string> = {};
  identityTypeSearch = '';
  openDropdown: string | null = null;

  matchingClients = signal<ClientChoice[]>([]);

  form = signal<PublicForm | null>(null);
  link = signal<FormLink | null>(null);
  type = signal<string | null>(null);

  otpSent = signal(false);
  otpMsg = signal('');
  otpCode = '';

  unlocked = signal(false);

  challengeId = signal<string | null>(null);
  verificationToken = signal<string | null>(null);

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

  private normalizeGender(
    value: string | null | undefined
  ): string | null {
    const gender = String(value ?? '').trim().toLowerCase();

    if (!gender) {
      return null;
    }

    switch (gender) {
      case 'male':
        return 'Male';
      case 'female':
        return 'Female';
      case 'other':
        return 'Other';
      default:
        return gender.charAt(0).toUpperCase() + gender.slice(1);
    }
  }

  private configuredFields(): ConfiguredField[] {
    return (this.form()?.fields ?? []) as ConfiguredField[];
  }

  private isIdentityField(fieldName: string): boolean {
    return (IDENTITY_FIELDS as readonly string[]).includes(fieldName);
  }

  private isPersonalDetailsField(fieldName: string): boolean {
    if (this.isIdentityField(fieldName)) {
      return false;
    }

    if ((PERSONAL as readonly string[]).includes(fieldName)) {
      return true;
    }

    const configuredField = this.configuredFields().find(
      field => field.fieldName === fieldName
    );

    return String(configuredField?.section ?? '')
      .trim()
      .toUpperCase() === 'APPLICANT_DETAILS';
  }

  fieldsInSection(section: string): ConfiguredField[] {
    const expected = section.trim().toUpperCase();

    return this.configuredFields()
      .filter(field =>
        String(field.section ?? '').trim().toUpperCase() === expected
      )
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  }

  applicantFields = computed(() =>
    this.fieldsInSection('APPLICANT_DETAILS')
  );

  governmentIdFields = computed(() =>
    this.fieldsInSection('GOVERNMENT_ID')
  );

  travelFields = computed(() =>
    this.fieldsInSection('TRAVEL_DETAILS')
  );

  otherFields = computed(() =>
    (this.form()?.fields ?? []).filter(
      field =>
        !(APPLICANT_FIELDS as readonly string[]).includes(field.fieldName) &&
        !(this.type()?.toUpperCase() === 'TRAVEL' &&
          field.fieldName === 'travellers')
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

  getInternationalPhone(): string {
    const enteredPhone = String(this.a.phone ?? '').trim();

    if (!enteredPhone) {
      return '';
    }

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

    if (
      localDigits.startsWith(callingCodeDigits) &&
      localDigits.length > 10
    ) {
      return `+${localDigits}`;
    }

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

  /**
   * Reset form for a new customer.
   * Preserve only the verified mobile number.
   */
  private resetForNewCustomer() {
    const verifiedPhone = this.a.phone;

    this.a = {
      name: null,
      email: null,
      phone: verifiedPhone,
      dateOfBirth: null,
      gender: null,
      nationality: null,
      countryOfResidence: null,
      identityCountry: null,
      identityType: null,
      identityNumber: null
    };

    this.values = {};

    if (this.type()?.toUpperCase() === 'TRAVEL') {
      this.values['travellers'] = '1';
    }

    this.matchingClients.set([]);
    this.loadedExistingClient = false;
    this.idTypes.set([]);

    this.clearDropdownState();
    this.syncCountrySelections();
  }

  private clearDropdownState() {
    this.countrySearch = {};
    this.identityTypeSearch = '';
    this.openDropdown = null;
  }

  onNewPhoneChange() {
    if (this.token) {
      return;
    }

    this.otpSent.set(false);
    this.otpMsg.set('');
    this.otpCode = '';

    this.challengeId.set(null);
    this.verificationToken.set(null);

    this.unlocked.set(false);
    this.matchingClients.set([]);

    if (this.loadedExistingClient) {
      this.a.name = null;
      this.a.email = null;
      this.a.dateOfBirth = null;
      this.a.gender = null;
      this.a.nationality = null;
      this.a.countryOfResidence = null;

      for (const field of this.configuredFields()) {
        if (
          this.isPersonalDetailsField(field.fieldName) &&
          !(PERSONAL as readonly string[]).includes(field.fieldName)
        ) {
          delete this.values[field.fieldName];
        }
      }

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
    this.matchingClients.set([]);

    this.loadedExistingClient = false;

    this.a = {
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

    this.values = {};
    this.clearDropdownState();

    this.router.navigate(['/form', code]);

    this.loading.set(true);
    this.loadForm(code);
  }

  private loadForm(code: string) {
    this.type.set(code);

    this.api.publicForm(code).subscribe({
      next: form => {
        this.form.set(form);

        // Temporary diagnostics to verify configured API fields.
        console.log('========== FORM API DEBUG ==========');
        console.log('Insurance type:', code);
        console.log('Complete form response:', form);

        console.table(
          (form.fields as any[]).map(field => ({
            fieldName: field.fieldName,
            displayLabel: field.displayLabel,
            section: field.section,
            sortOrder: field.sortOrder,
            isMandatory: field.isMandatory,
            dataType: field.dataType
          }))
        );

        console.log(
          'Government ID fields:',
          (form.fields as any[]).filter(field =>
            String(field.section ?? '').trim().toUpperCase() ===
            'GOVERNMENT_ID'
          )
        );

        console.log(
          'Travel fields:',
          (form.fields as any[]).filter(field =>
            String(field.section ?? '').trim().toUpperCase() ===
            'TRAVEL_DETAILS'
          )
        );

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

  // ------------------------------------------------------------
  // Form-link prefill
  // ------------------------------------------------------------

  private applyPrefill(link: FormLink) {
    const client = link.client;

    if (client) {
      this.a.name = client.name ?? null;
      this.a.email = client.email ?? null;
      this.a.dateOfBirth = client.dateOfBirth ?? null;
      this.a.gender = this.normalizeGender(client.gender);
      this.a.nationality = client.nationality ?? null;
      this.a.countryOfResidence = client.countryOfResidence ?? null;

      this.a.phone = null;
    }

    for (const [key, value] of Object.entries(link.prefill ?? {})) {
      if (link.existingClient) {
        if (!this.isPersonalDetailsField(key)) {
          continue;
        }

        if (key === 'phone') {
          continue;
        }

        if (key in this.a) {
          (this.a as unknown as Record<string, string | null>)[key] =
            key === 'gender'
              ? this.normalizeGender(value)
              : value;
        } else {
          this.values[key] = value;
        }

        continue;
      }

      if (this.isIdentityField(key)) {
        continue;
      }

      if ((PERSONAL as readonly string[]).includes(key)) {
        if (key === 'phone') {
          continue;
        }

        (this.a as unknown as Record<string, string | null>)[key] =
          key === 'gender'
            ? this.normalizeGender(value)
            : value;
      } else {
        this.values[key] = value;
      }
    }

    this.syncCountrySelections();
  }

  // ------------------------------------------------------------
  // Configured field value access
  // ------------------------------------------------------------

  getConfiguredFieldValue(fieldName: string): string | null {
    const applicantValue = (this.a as unknown as Record<
      string,
      string | null
    >)[fieldName];

    if (applicantValue !== undefined) {
      return applicantValue;
    }

    return this.values[fieldName] ?? '';
  }

  setConfiguredFieldValue(fieldName: string, value: string | null) {
    if (
      (APPLICANT_FIELDS as readonly string[]).includes(fieldName)
    ) {
      (this.a as unknown as Record<string, string | null>)[fieldName] =
        fieldName === 'gender'
          ? this.normalizeGender(value)
          : value;
      return;
    }

    this.values[fieldName] = value ?? '';
  }

  isCountryField(fieldName: string): boolean {
    return [
      'countryOfResidence',
      'identityCountry',
      'passportCountry',
      'passportIssuingCountry',
      'originCountry',
      'departureCountry',
      'destinationCountry'
    ].includes(fieldName);
  }

  isSearchableCountryField(fieldName: string): boolean {
    return (SEARCHABLE_COUNTRY_FIELDS as readonly string[])
      .includes(fieldName);
  }

  isIdentityTypeField(fieldName: string): boolean {
    return fieldName === 'identityType';
  }

  isGenderField(fieldName: string): boolean {
    return fieldName === 'gender';
  }

  isPhoneField(fieldName: string): boolean {
    return fieldName === 'phone';
  }

  isIdentityNumberField(fieldName: string): boolean {
    return fieldName === 'identityNumber';
  }

  onConfiguredFieldChange(fieldName: string, value: string | null) {
    if (fieldName === 'identityCountry') {
      this.onIdCountry(value);
      return;
    }

    this.setConfiguredFieldValue(fieldName, value);
  }

  // ------------------------------------------------------------
  // Custom searchable country dropdown
  // ------------------------------------------------------------

  setCountrySearch(fieldName: string, value: string): void {
    this.countrySearch[fieldName] = value ?? '';
  }

  getCountrySearch(fieldName: string): string {
    return this.countrySearch[fieldName] ?? '';
  }

  toggleCountryDropdown(fieldName: string): void {
    const dropdownId = `country:${fieldName}`;

    if (this.openDropdown === dropdownId) {
      this.openDropdown = null;
      return;
    }

    this.openDropdown = dropdownId;
    this.countrySearch[fieldName] = '';
  }

  isCountryDropdownOpen(fieldName: string): boolean {
    return this.openDropdown === `country:${fieldName}`;
  }

  filteredCountries(fieldName: string): PublicCountry[] {
    const search = this.getCountrySearch(fieldName)
      .trim()
      .toLowerCase();

    if (!search) {
      return this.countries();
    }

    return this.countries().filter(country =>
      String(country.name ?? '').toLowerCase().includes(search) ||
      String(country.code ?? '').toLowerCase().includes(search)
    );
  }

  countryInputValue(fieldName: string): string {
    const selectedCode = this.getConfiguredFieldValue(fieldName);

    return this.countries().find(country =>
      String(country.code ?? '').toUpperCase() ===
      String(selectedCode ?? '').toUpperCase()
    )?.name ?? '';
  }

  onCountrySearchChange(
    fieldName: string,
    value: string
  ): void {
    this.countrySearch[fieldName] = value ?? '';
  }

  selectCountry(
    fieldName: string,
    country: PublicCountry
  ): void {
    if (fieldName === 'identityCountry') {
      this.onIdCountry(country.code);
    } else {
      this.setConfiguredFieldValue(fieldName, country.code);
    }

    this.countrySearch[fieldName] = '';
    this.openDropdown = null;
  }

  // ------------------------------------------------------------
  // Custom searchable identity document type dropdown
  // ------------------------------------------------------------

  setIdentityTypeSearch(value: string): void {
    this.identityTypeSearch = value ?? '';
  }

  toggleIdentityTypeDropdown(): void {
    if (!this.a.identityCountry || this.busy()) {
      return;
    }

    if (this.openDropdown === 'identityType') {
      this.openDropdown = null;
      return;
    }

    this.openDropdown = 'identityType';
    this.identityTypeSearch = '';
  }

  isIdentityTypeDropdownOpen(): boolean {
    return this.openDropdown === 'identityType';
  }

  filteredIdentityTypes(): PublicIdentityType[] {
    const search = this.identityTypeSearch.trim().toLowerCase();

    if (!search) {
      return this.idTypes();
    }

    return this.idTypes().filter(type =>
      String(type.label ?? '').toLowerCase().includes(search) ||
      String(type.code ?? '').toLowerCase().includes(search)
    );
  }

  identityTypeInputValue(): string {
    return this.idTypes().find(type =>
      type.code === this.a.identityType
    )?.label ?? '';
  }

  onIdentityTypeSearchChange(value: string): void {
    this.identityTypeSearch = value ?? '';
  }

  selectIdentityType(type: PublicIdentityType): void {
    this.a.identityType = type.code;
    this.identityTypeSearch = '';
    this.openDropdown = null;
  }

  // ------------------------------------------------------------
  // Send phone OTP
  // ------------------------------------------------------------

  sendOtp() {
    this.busy.set(true);
    this.otpMsg.set('');
    this.error.set('');

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

        if (
          response.ambiguousPhone &&
          response.clients &&
          response.clients.length > 1
        ) {
          this.matchingClients.set(response.clients);
          this.unlocked.set(false);

          this.otpMsg.set(
            'Phone verified successfully. Please select your name.'
          );

          return;
        }

        if (response.client) {
          this.matchingClients.set([]);

          this.loadExistingClient(response.client);
          this.unlocked.set(true);

          this.otpMsg.set(
            'Phone verified successfully. Your personal details have been loaded.'
          );

          return;
        }

        if (response.newCustomerAllowed && !response.client) {
          this.resetForNewCustomer();

          this.unlocked.set(true);

          this.otpMsg.set(
            'Phone verified successfully. Please enter your personal details.'
          );

          return;
        }

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
  // Select an existing customer
  // ------------------------------------------------------------

  selectExistingClient(client: ClientChoice) {
    const verificationToken = this.verificationToken();

    if (!verificationToken) {
      this.otpMsg.set(
        'Your phone verification has expired. Please verify your phone again.'
      );
      return;
    }

    if (!client.clientCode) {
      this.otpMsg.set('Invalid customer selection. Please try again.');
      return;
    }

    this.busy.set(true);
    this.otpMsg.set('');

    this.api.selectExistingClient(
      verificationToken,
      client.clientCode
    ).subscribe({
      next: (response: SelectExistingClientResult) => {
        this.busy.set(false);

        if (!response.verified || !response.client) {
          this.unlocked.set(false);

          this.otpMsg.set(
            response.message ||
            'Unable to load the selected customer. Please try again.'
          );
          return;
        }

        this.loadExistingClient(response.client);

        this.matchingClients.set([]);
        this.unlocked.set(true);

        this.otpMsg.set(
          `Welcome back, ${response.client.name || 'customer'}. Your personal details have been loaded.`
        );
      },
      error: e => {
        this.busy.set(false);
        this.unlocked.set(false);
        this.otpMsg.set(errorMessage(e));
      }
    });
  }

  // ------------------------------------------------------------
  // Load selected customer's personal details
  // ------------------------------------------------------------

  private loadExistingClient(client: FormLinkClient) {
    this.a.name = client.name ?? null;
    this.a.email = client.email ?? null;
    this.a.dateOfBirth = client.dateOfBirth ?? null;
    this.a.gender = this.normalizeGender(client.gender);
    this.a.nationality = client.nationality ?? null;
    this.a.countryOfResidence = client.countryOfResidence ?? null;

    this.a.identityCountry = null;
    this.a.identityType = null;
    this.a.identityNumber = null;

    for (const field of this.configuredFields()) {
      const fieldName = field.fieldName;

      if (
        !this.isPersonalDetailsField(fieldName) ||
        (PERSONAL as readonly string[]).includes(fieldName)
      ) {
        delete this.values[fieldName];
      }
    }

    const clientData = client as unknown as Record<
      string,
      string | null | undefined
    >;

    for (const [key, value] of Object.entries(clientData)) {
      if (
        value === null ||
        value === undefined ||
        key === 'clientCode' ||
        key === 'phoneMasked' ||
        key === 'identityNumberMasked' ||
        key === 'identityCountry' ||
        key === 'identityType' ||
        key === 'identityNumber' ||
        key === 'phone'
      ) {
        continue;
      }

      if (!this.isPersonalDetailsField(key)) {
        continue;
      }

      if (key in this.a) {
        (this.a as unknown as Record<string, string | null>)[key] =
          key === 'gender'
            ? this.normalizeGender(value)
            : value;
      } else {
        this.values[key] = String(value);
      }
    }

    this.syncCountrySelections();

    this.loadedExistingClient = true;
    this.idTypes.set([]);

    this.clearDropdownState();
  }

  // ------------------------------------------------------------
  // Identity document types
  // ------------------------------------------------------------

  onIdCountry(code: string | null) {
    this.a.identityCountry = code;
    this.a.identityType = null;

    this.identityTypeSearch = '';
    this.openDropdown = null;

    this.refreshIdTypes();
  }

  private refreshIdTypes() {
    const countryCode = this.a.identityCountry;

    if (!countryCode) {
      this.idTypes.set([]);
      return;
    }

    this.idTypes.set([]);

    this.api.publicIdentityTypes(countryCode).subscribe({
      next: types => {
        // Ignore results if the user has selected a different country
        // while the previous request was still running.
        if (this.a.identityCountry !== countryCode) {
          return;
        }

        this.idTypes.set(types);
      },
      error: () => {
        if (this.a.identityCountry === countryCode) {
          this.idTypes.set([]);
        }
      }
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
  // TRAVEL configured-field validation
  // ------------------------------------------------------------

  private validateTravelConfiguredFields(): boolean {
    for (const field of this.configuredFields()) {
      const fieldName = field.fieldName;
      let value = this.getConfiguredFieldValue(fieldName);

      if (fieldName === 'phone') {
        value = this.getInternationalPhone();
      }

      const enteredValue = String(value ?? '').trim();

      if (!enteredValue) {
        if (field.isMandatory) {
          this.error.set(
            `${field.displayLabel} is required. Please enter it before submitting.`
          );
          return false;
        }

        continue;
      }

      const dataType = String(field.dataType ?? '').toUpperCase();

      if (
        dataType.includes('EMAIL') &&
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(enteredValue)
      ) {
        this.error.set(`Please enter a valid ${field.displayLabel}.`);
        return false;
      }

      if (fieldName === 'phone' || dataType.includes('PHONE')) {
        const digits = enteredValue.replace(/\D/g, '');

        if (digits.length < 7 || digits.length > 15) {
          this.error.set(`Please enter a valid ${field.displayLabel}.`);
          return false;
        }
      }

      if (
        (dataType.includes('NUMBER') || dataType.includes('INTEGER')) &&
        !Number.isFinite(Number(enteredValue))
      ) {
        this.error.set(`Please enter a valid number for ${field.displayLabel}.`);
        return false;
      }

      if (dataType.includes('DATE')) {
        const dateParts = enteredValue.match(/^(\d{4})-(\d{2})-(\d{2})$/);

        if (dateParts) {
          const year = Number(dateParts[1]);
          const month = Number(dateParts[2]);
          const day = Number(dateParts[3]);
          const date = new Date(Date.UTC(year, month - 1, day));

          if (
            date.getUTCFullYear() !== year ||
            date.getUTCMonth() !== month - 1 ||
            date.getUTCDate() !== day
          ) {
            this.error.set(`Please enter a valid date for ${field.displayLabel}.`);
            return false;
          }
        } else {
          this.error.set(`Please enter a valid date for ${field.displayLabel}.`);
          return false;
        }
      }

      const regex = field.validationRegex?.trim();

      if (regex) {
        try {
          const pattern = new RegExp(regex);

          if (!pattern.test(enteredValue)) {
            this.error.set(
              `Please enter ${field.displayLabel} in the correct format.`
            );
            return false;
          }
        } catch {
          this.error.set(
            `The validation rule for ${field.displayLabel} is incorrectly configured. Please contact the administrator.`
          );
          return false;
        }
      }
    }

    return true;
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

    if (code.toUpperCase() === 'TRAVEL') {
      if (!this.validateTravelConfiguredFields()) {
        this.busy.set(false);
        return;
      }

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
        passportCountry
      };

      const travelPayload = {
        applicant: travelApplicant,
        fields: { ...this.values },
        additionalTravellers: [],
        travellers: travellerCount,
        departureCountry:
          this.values['originCountry'] ??
          this.values['departureCountry'],
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
