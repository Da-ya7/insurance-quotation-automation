import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { Quotationrequest } from './quotationrequest';

describe('Quotationrequest', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [Quotationrequest],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(Quotationrequest);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
