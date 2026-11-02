import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { ClientForm } from './client-form';

describe('ClientForm', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [ClientForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(ClientForm);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
