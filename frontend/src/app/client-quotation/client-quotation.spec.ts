import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ClientQuotation } from './client-quotation';

describe('ClientQuotation', () => {
  let component: ClientQuotation;
  let fixture: ComponentFixture<ClientQuotation>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClientQuotation]
    })
      .compileComponents();

    fixture = TestBed.createComponent(ClientQuotation);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
