import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { ClientQuotation } from './client-quotation';

describe('ClientQuotation', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [ClientQuotation],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(ClientQuotation);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
