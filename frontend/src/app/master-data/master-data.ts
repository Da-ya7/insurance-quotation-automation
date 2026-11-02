import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  ApiService, CountryDto, EmailKeywordDto, FieldDataType, IdentityTypeDto, InsuranceTypeDto, RequiredFieldDto
} from '../core/api.service';
import { isAdmin } from '../core/auth';
import { errorMessage } from '../core/http-error';
import { goBackOrHome } from '../core/nav';

type Tab = 'types' | 'fields' | 'identity' | 'countries' | 'keywords';

@Component({
  selector: 'app-master-data',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './master-data.html',
  styleUrl: './master-data.css'
})
export class MasterData implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);
  private location = inject(Location);

  readonly admin = isAdmin();
  readonly dataTypes: FieldDataType[] = ['TEXT', 'NUMBER', 'DATE', 'EMAIL', 'PHONE'];
  readonly tabs: { id: Tab; label: string }[] = [
    { id: 'types', label: 'Insurance types' },
    { id: 'fields', label: 'Required fields' },
    { id: 'identity', label: 'Identity documents' },
    { id: 'countries', label: 'Countries' },
    { id: 'keywords', label: 'Email subject keywords' }
  ];

  tab: Tab = 'types';
  loading = false;
  error = '';
  ok = '';

  types: InsuranceTypeDto[] = [];
  newType = { code: '', label: '' };

  fieldType = '';
  fields: RequiredFieldDto[] = [];
  newField = this.blankField();

  countries: CountryDto[] = [];
  identityCountry = '*';
  identityTypes: IdentityTypeDto[] = [];
  newIdentity = { code: '', label: '', validationRegex: '', example: '' };

  keywords: EmailKeywordDto[] = [];
  newKeyword = '';

  ngOnInit(): void {
    this.open('types');
  }

  private blankField() {
    return {
      fieldName: '', displayLabel: '', dataType: 'TEXT' as FieldDataType,
      isMandatory: true, validationRegex: '', sortOrder: 100, helpText: ''
    };
  }

  private fail(err: unknown, fallback: string): void {
    this.error = errorMessage(err, fallback);
    this.ok = '';
    this.loading = false;
    this.cdr.markForCheck();
  }

  private done(message: string): void {
    this.ok = message;
    this.error = '';
    this.cdr.markForCheck();
  }

  open(tab: Tab): void {
    this.tab = tab;
    this.error = '';
    this.ok = '';
    this.loading = true;

    switch (tab) {
      case 'types':
        this.loadTypes();
        break;
      case 'fields':
        this.loadTypes(() => {
          if (!this.fieldType && this.types.length) {
            this.fieldType = this.types[0].code;
          }
          this.loadFields();
        });
        break;
      case 'identity':
        this.api.countries().subscribe({
          next: c => { this.countries = c; this.loadIdentity(); },
          error: err => this.fail(err, 'Could not load countries.')
        });
        break;
      case 'countries':
        this.api.countries().subscribe({
          next: c => { this.countries = c; this.loading = false; this.cdr.markForCheck(); },
          error: err => this.fail(err, 'Could not load countries.')
        });
        break;
      case 'keywords':
        this.loadKeywords();
        break;
    }
  }

  // ---------------------------------------------------------- insurance types
  private loadTypes(then?: () => void): void {
    this.api.insuranceTypes().subscribe({
      next: t => {
        this.types = t.map(x => ({ ...x }));
        this.loading = false;
        this.cdr.markForCheck();
        then?.();
      },
      error: err => this.fail(err, 'Could not load insurance types.')
    });
  }

  saveType(t: InsuranceTypeDto): void {
    this.api.updateInsuranceType(t.code, t).subscribe({
      next: () => this.done(`Saved ${t.label}.`),
      error: err => this.fail(err, 'Could not save.')
    });
  }

  addType(): void {
    const code = this.newType.code.trim().toUpperCase();
    if (!code || !this.newType.label.trim()) {
      this.error = 'Code and label are required.';
      return;
    }
    this.api.createInsuranceType({
      code, label: this.newType.label.trim(), active: true, sortOrder: this.types.length + 1
    }).subscribe({
      next: () => { this.newType = { code: '', label: '' }; this.done('Insurance type added.'); this.loadTypes(); },
      error: err => this.fail(err, 'Could not add the insurance type.')
    });
  }

  // ---------------------------------------------------------- required fields
  loadFields(): void {
    if (!this.fieldType) {
      this.fields = [];
      this.loading = false;
      this.cdr.markForCheck();
      return;
    }
    this.loading = true;
    this.api.requiredFields(this.fieldType).subscribe({
      next: f => {
        this.fields = f.map(x => ({ ...x })).sort((a, b) => a.sortOrder - b.sortOrder);
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: err => this.fail(err, 'Could not load required fields.')
    });
  }

  saveField(f: RequiredFieldDto): void {
    this.api.updateRequiredField(f.id, f).subscribe({
      next: () => this.done(`Saved ${f.displayLabel}.`),
      error: err => this.fail(err, 'Could not save.')
    });
  }

  addField(): void {
    const n = this.newField;
    if (!n.fieldName.trim() || !n.displayLabel.trim()) {
      this.error = 'Field name and label are required.';
      return;
    }
    this.api.createRequiredField({
      fieldName: n.fieldName.trim(),
      displayLabel: n.displayLabel.trim(),
      dataType: n.dataType,
      policyType: this.fieldType,
      isActive: true,
      isMandatory: n.isMandatory,
      validationRegex: n.validationRegex.trim() || null,
      sortOrder: n.sortOrder,
      helpText: n.helpText.trim() || null
    }).subscribe({
      next: () => { this.newField = this.blankField(); this.done('Field added.'); this.loadFields(); },
      error: err => this.fail(err, 'Could not add the field.')
    });
  }

  // ---------------------------------------------------------- identity documents
  loadIdentity(): void {
    this.loading = true;
    this.api.identityTypes(this.identityCountry).subscribe({
      next: t => {
        this.identityTypes = t.map(x => ({ ...x }));
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: err => this.fail(err, 'Could not load identity documents.')
    });
  }

  saveIdentity(t: IdentityTypeDto): void {
    this.api.updateIdentityType(t.id, t).subscribe({
      next: () => this.done(`Saved ${t.label}.`),
      error: err => this.fail(err, 'Could not save.')
    });
  }

  addIdentity(): void {
    const n = this.newIdentity;
    if (!n.code.trim() || !n.label.trim()) {
      this.error = 'Code and label are required.';
      return;
    }
    this.api.createIdentityType({
      countryCode: this.identityCountry,
      code: n.code.trim().toUpperCase(),
      label: n.label.trim(),
      validationRegex: n.validationRegex.trim() || null,
      example: n.example.trim() || null,
      active: true
    }).subscribe({
      next: () => {
        this.newIdentity = { code: '', label: '', validationRegex: '', example: '' };
        this.done('Identity document added.');
        this.loadIdentity();
      },
      error: err => this.fail(err, 'Could not add the identity document.')
    });
  }

  // ---------------------------------------------------------- countries
  toggleCountry(c: CountryDto): void {
    this.api.updateCountry(c.code, { name: c.name, active: c.active }).subscribe({
      next: () => this.done(`${c.name} ${c.active ? 'enabled' : 'disabled'}.`),
      error: err => {
        c.active = !c.active; // roll back the toggle
        this.fail(err, 'Could not save.');
      }
    });
  }

  // ---------------------------------------------------------- keywords
  loadKeywords(): void {
    this.api.emailKeywords().subscribe({
      next: k => { this.keywords = k; this.loading = false; this.cdr.markForCheck(); },
      error: err => this.fail(err, 'Could not load keywords.')
    });
  }

  addKeyword(): void {
    const k = this.newKeyword.trim();
    if (!k) {
      return;
    }
    this.api.createEmailKeyword(k).subscribe({
      next: () => { this.newKeyword = ''; this.done('Keyword added.'); this.loadKeywords(); },
      error: err => this.fail(err, 'Could not add the keyword.')
    });
  }

  toggleKeyword(k: EmailKeywordDto): void {
    this.api.updateEmailKeyword(k.id, k.active).subscribe({
      next: () => this.done('Keyword updated.'),
      error: err => {
        k.active = !k.active;
        this.fail(err, 'Could not save.');
      }
    });
  }

  removeKeyword(k: EmailKeywordDto): void {
    if (!window.confirm(`Remove keyword "${k.keyword}"?`)) {
      return;
    }
    this.api.deleteEmailKeyword(k.id).subscribe({
      next: () => { this.done('Keyword removed.'); this.loadKeywords(); },
      error: err => this.fail(err, 'Could not remove the keyword.')
    });
  }

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
