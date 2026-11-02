import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { Clients } from './clients';

describe('Clients', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [Clients],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(Clients);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
