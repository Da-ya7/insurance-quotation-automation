import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Quotationrequest } from './quotationrequest';

describe('Quotationrequest', () => {
  let component: Quotationrequest;
  let fixture: ComponentFixture<Quotationrequest>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Quotationrequest],
    }).compileComponents();

    fixture = TestBed.createComponent(Quotationrequest);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
