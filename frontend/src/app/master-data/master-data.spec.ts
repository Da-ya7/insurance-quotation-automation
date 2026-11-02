import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { MasterData } from './master-data';

describe('MasterData', () => {
  it('can be created', () => {
    TestBed.configureTestingModule({
      imports: [MasterData],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    const fixture = TestBed.createComponent(MasterData);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
