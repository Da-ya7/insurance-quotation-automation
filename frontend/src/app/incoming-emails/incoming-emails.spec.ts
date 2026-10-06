import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IncomingEmails } from './incoming-emails';

describe('IncomingEmails', () => {
  let component: IncomingEmails;
  let fixture: ComponentFixture<IncomingEmails>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IncomingEmails],
    }).compileComponents();

    fixture = TestBed.createComponent(IncomingEmails);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
