import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MissingInformation } from './missing-information';

describe('MissingInformation', () => {
  let component: MissingInformation;
  let fixture: ComponentFixture<MissingInformation>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MissingInformation],
    }).compileComponents();

    fixture = TestBed.createComponent(MissingInformation);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
