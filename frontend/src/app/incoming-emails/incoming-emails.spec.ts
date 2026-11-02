import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { IncomingEmails } from './incoming-emails';

describe('IncomingEmails', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [IncomingEmails],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(IncomingEmails);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
