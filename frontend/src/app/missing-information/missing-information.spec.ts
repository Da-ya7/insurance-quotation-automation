import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { MissingInformation } from './missing-information';

describe('MissingInformation', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [MissingInformation],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(MissingInformation);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
