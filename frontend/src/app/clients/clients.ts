
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ApiService,
  ClientDto,
  ClientUpdate,
  ClientVersionDto,
  CountryDto,
  IdentityTypeDto
} from '../core/api.service';
import { avatarColor, initials, shortDate, statusLabel } from '../core/format';
import { errorMessage } from '../core/http-error';
import { goBackOrHome } from '../core/nav';

interface EditForm {
  name: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  gender: string;
  nationality: string;
  countryOfResidence: string;
  identityCountry: string;
  identityType: string;
  identityNumber: string;
}

@Component({
  selector: 'app-clients',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './clients.html',
  styleUrl: './clients.css'
})
export class Clients implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private location = inject(Location);

  clients: ClientDto[] = [];
  searchText = '';
  loading = true;
  errorMessage = '';

  // PAGINATION
  readonly pageSize = 5;
  currentPage = 0;

  selected: ClientDto | null = null;
  versions: ClientVersionDto[] = [];
  detailError = '';

  editing = false;
  saving = false;
  editError = '';
  editOk = '';

  form: EditForm = this.emptyForm();
  countries: CountryDto[] = [];
  identityTypes: IdentityTypeDto[] = [];

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly shortDate = shortDate;
  readonly statusLabel = statusLabel;

  ngOnInit(): void {
    this.load();
  }

  // --------------------------------------------------------------
  // LOAD CLIENTS
  // --------------------------------------------------------------

  load(): void {
    this.loading = true;
    this.errorMessage = '';

    this.api.clients().subscribe({
      next: data => {
        this.clients = data;
        this.loading = false;

        // Keep the selected record in sync with the refreshed list.
        if (this.selected) {
          this.selected =
            data.find(c => c.id === this.selected!.id) ?? this.selected;
        }

        // Keep the current page valid after refreshing.
        this.adjustPage();

        // Open a client if an ID is provided in the URL.
        const id = Number(this.route.snapshot.queryParamMap.get('id'));

        if (!this.selected && id) {
          const client = data.find(x => x.id === id);

          if (client) {
            this.select(client);
          }
        }

        this.cdr.markForCheck();
      },

      error: err => {
        this.errorMessage = errorMessage(err, 'Could not load clients.');
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  // --------------------------------------------------------------
  // SEARCH AND FILTER
  // --------------------------------------------------------------

  get filteredClients(): ClientDto[] {
    const q = this.searchText.trim().toLowerCase();

    return this.clients.filter(c =>
      !q ||
      (c.name ?? '').toLowerCase().includes(q) ||
      (c.clientCode ?? '').toLowerCase().includes(q) ||
      (c.email ?? '').toLowerCase().includes(q) ||
      (c.phone ?? '').toLowerCase().includes(q)
    );
  }

  onSearchChange(): void {
    // Always start from the first page after searching.
    this.currentPage = 0;
  }

  // --------------------------------------------------------------
  // PAGINATION
  // --------------------------------------------------------------

  get paginatedClients(): ClientDto[] {
    const start = this.currentPage * this.pageSize;

    return this.filteredClients.slice(
      start,
      start + this.pageSize
    );
  }

  get totalPages(): number {
    return Math.ceil(this.filteredClients.length / this.pageSize);
  }

  get showingFrom(): number {
    if (this.filteredClients.length === 0) {
      return 0;
    }

    return this.currentPage * this.pageSize + 1;
  }

  get showingTo(): number {
    return Math.min(
      (this.currentPage + 1) * this.pageSize,
      this.filteredClients.length
    );
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
    }
  }

  private adjustPage(): void {
    const lastPage = Math.max(0, this.totalPages - 1);

    if (this.currentPage > lastPage) {
      this.currentPage = lastPage;
    }
  }

  // --------------------------------------------------------------
  // SELECT CLIENT
  // --------------------------------------------------------------

  select(c: ClientDto): void {
    this.selected = c;
    this.versions = [];
    this.detailError = '';
    this.editing = false;
    this.editError = '';
    this.editOk = '';

    this.api.client(c.id).subscribe({
      next: full => {
        if (this.selected?.id === full.id) {
          this.selected = full;
          this.cdr.markForCheck();
        }
      },

      error: () => {
        // The list row already holds the client data.
      }
    });

    this.loadVersions(c.id);

    setTimeout(() => {
      document
        .getElementById('client-detail')
        ?.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  }

  // --------------------------------------------------------------
  // CLIENT VERSION HISTORY
  // --------------------------------------------------------------

  private loadVersions(id: number): void {
    this.api.clientVersions(id).subscribe({
      next: v => {
        // Newest version first.
        this.versions = [...v].sort(
          (a, b) => b.versionNo - a.versionNo
        );

        this.cdr.markForCheck();
      },

      error: err => {
        this.detailError = errorMessage(
          err,
          'Could not load version history.'
        );

        this.cdr.markForCheck();
      }
    });
  }

  closeDetail(): void {
    this.selected = null;
    this.editing = false;
  }

  // --------------------------------------------------------------
  // EDIT FORM
  // --------------------------------------------------------------

  private emptyForm(): EditForm {
    return {
      name: '',
      email: '',
      phone: '',
      dateOfBirth: '',
      gender: '',
      nationality: '',
      countryOfResidence: '',
      identityCountry: '',
      identityType: '',
      identityNumber: ''
    };
  }

  private formFrom(c: ClientDto): EditForm {
    return {
      name: c.name ?? '',
      email: c.email ?? '',
      phone: c.phone ?? '',
      dateOfBirth: c.dateOfBirth ?? '',
      gender: c.gender ?? '',
      nationality: c.nationality ?? '',
      countryOfResidence: c.countryOfResidence ?? '',
      identityCountry: c.identityCountry ?? '',
      identityType: c.identityType ?? '',
      identityNumber: ''
    };
  }

  startEdit(): void {
    if (!this.selected) {
      return;
    }

    this.form = this.formFrom(this.selected);
    this.editing = true;
    this.editError = '';
    this.editOk = '';

    if (this.countries.length === 0) {
      this.api.countries().subscribe({
        next: c => {
          this.countries = c;
          this.cdr.markForCheck();
        },

        error: () => {
          // Country list remains empty if the request fails.
        }
      });
    }

    this.loadIdentityTypes();
  }

  cancelEdit(): void {
    this.editing = false;
    this.editError = '';
  }

  loadIdentityTypes(resetType = false): void {
    if (resetType) {
      this.form.identityType = '';
    }

    if (!this.form.identityCountry) {
      this.identityTypes = [];
      return;
    }

    this.api.identityTypes(this.form.identityCountry).subscribe({
      next: t => {
        this.identityTypes = t;
        this.cdr.markForCheck();
      },

      error: () => {
        this.identityTypes = [];
        this.cdr.markForCheck();
      }
    });
  }

  // --------------------------------------------------------------
  // DETECT CHANGED FIELDS
  // --------------------------------------------------------------

  // Only fields that really changed are sent.
  // Identity number is write-only.
  private changes(): ClientUpdate {
    const c = this.selected!;
    const f = this.form;

    const body: ClientUpdate = {};

    const cmp: [keyof ClientUpdate, string, string | null][] = [
      ['name', f.name, c.name],
      ['email', f.email, c.email],
      ['phone', f.phone, c.phone],
      ['dateOfBirth', f.dateOfBirth, c.dateOfBirth],
      ['gender', f.gender, c.gender],
      ['nationality', f.nationality, c.nationality],
      ['countryOfResidence', f.countryOfResidence, c.countryOfResidence],
      ['identityCountry', f.identityCountry, c.identityCountry],
      ['identityType', f.identityType, c.identityType]
    ];

    for (const [key, value, old] of cmp) {
      const v = value.trim();

      if (v !== '' && v !== (old ?? '')) {
        body[key] = v;
      }
    }

    if (f.identityNumber.trim()) {
      body.identityNumber = f.identityNumber.trim();
    }

    return body;
  }

  // --------------------------------------------------------------
  // SAVE CLIENT
  // --------------------------------------------------------------

  save(): void {
    if (!this.selected) {
      return;
    }

    const body = this.changes();

    if (Object.keys(body).length === 0) {
      this.editError = 'Nothing has changed.';
      return;
    }

    this.saving = true;
    this.editError = '';
    this.editOk = '';

    const id = this.selected.id;

    this.api.updateClient(id, body).subscribe({
      next: updated => {
        this.saving = false;
        this.editing = false;
        this.editOk = 'Client updated.';

        this.selected = updated;

        this.clients = this.clients.map(c =>
          c.id === updated.id ? updated : c
        );

        this.adjustPage();
        this.loadVersions(id);

        this.cdr.markForCheck();
      },

      error: err => {
        this.saving = false;

        // Display server-side validation and error messages.
        this.editError = errorMessage(
          err,
          'Could not save the client.'
        );

        this.cdr.markForCheck();
      }
    });
  }

  // --------------------------------------------------------------
  // NAVIGATION
  // --------------------------------------------------------------

  goBack(): void {
    goBackOrHome(this.location, this.router);
  }
}
