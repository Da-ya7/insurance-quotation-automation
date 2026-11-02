import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { Quotation } from './quotation';

describe('Quotation', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [Quotation],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(Quotation);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
