import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ApiService, MissingDto } from '../core/api.service';
import { avatarColor, initials, shortDate } from '../core/format';

@Component({
  selector: 'app-missing-information',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './missing-information.html',
  styleUrl: './missing-information.css'
})
export class MissingInformation implements OnInit {

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  constructor(private location: Location) {}

  missingItems: MissingDto[] = [];
  loading = true;
  errorMessage = '';

  readonly initials = initials;
  readonly avatarColor = avatarColor;
  readonly shortDate = shortDate;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';

    this.api.missingInformation().subscribe({
      next: data => {
        this.missingItems = data;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = 'Could not load missing information.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  get requestCount(): number {
    return new Set(this.missingItems.map(item => item.requestId)).size;
  }

  goBack(): void {
    this.location.back();
  }
}
