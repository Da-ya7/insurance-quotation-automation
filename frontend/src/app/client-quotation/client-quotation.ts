
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';

interface Traveller {
  name: string;
  dateOfBirth: string;
  age: number | null;
  passportNumber: string;
  gender: string;
  nationality: string;
  countryOfResidence: string;
  email: string;
  phone: string;
  preExistingMedicalConditions: string;
}

@Component({
  selector: 'app-client-quotation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './client-quotation.html',
  styleUrl: './client-quotation.css',
})
export class ClientQuotation {

  today = this.getTodayDate();

  // Main applicant personal details
  formData = {
    name: '',
    dateOfBirth: '',
    age: null as number | null,
    passportNumber: '',
    gender: '',
    nationality: '',
    countryOfResidence: '',
    email: '',
    phone: '',
    preExistingMedicalConditions: '',
    departureCountry: '',
    destinationCountry: '',
    travelStartDate: '',
    travelEndDate: '',
    purposeOfTravel: '',
    travellers: 1,
  };

  // Additional family members
  additionalTravellers: Traveller[] = [];

  errorMessage = '';
  successMessage = '';
  isSubmitting = false;

  createTraveller(): Traveller {
    return {
      name: '',
      dateOfBirth: '',
      age: null,
      passportNumber: '',
      gender: '',
      nationality: '',
      countryOfResidence: '',
      email: '',
      phone: '',
      preExistingMedicalConditions: '',
    };
  }

  addTraveller(): void {
    this.errorMessage = '';
    this.successMessage = '';

    const totalTravellers = 1 + this.additionalTravellers.length;

    if (totalTravellers >= 20) {
      this.errorMessage = 'Maximum 20 travellers are allowed.';
      return;
    }

    this.additionalTravellers.push(this.createTraveller());
  }

  removeTraveller(index: number): void {
    this.additionalTravellers.splice(index, 1);
    this.errorMessage = '';
    this.successMessage = '';
  }

  getTodayDate(): string {
    const date = new Date();
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
  }

  calculateAge(): void {
    this.formData.age = this.getAge(this.formData.dateOfBirth);
  }

  calculateTravellerAge(index: number): void {
    const traveller = this.additionalTravellers[index];

    if (traveller) {
      traveller.age = this.getAge(traveller.dateOfBirth);
    }
  }

  private getAge(dateOfBirth: string): number | null {
    if (!dateOfBirth) {
      return null;
    }

    const today = new Date();
    const dob = new Date(`${dateOfBirth}T00:00:00`);

    if (Number.isNaN(dob.getTime()) || dob > today) {
      return null;
    }

    let age = today.getFullYear() - dob.getFullYear();
    const monthDifference = today.getMonth() - dob.getMonth();

    if (
      monthDifference < 0 ||
      (monthDifference === 0 && today.getDate() < dob.getDate())
    ) {
      age--;
    }

    return age;
  }

  submitForm(form: NgForm): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (form.invalid) {
      form.control.markAllAsTouched();
      this.errorMessage =
        'Please fill in all required fields correctly.';
      return;
    }

    if (this.formData.age === null) {
      this.errorMessage = 'Please enter a valid date of birth.';
      return;
    }

    const invalidTraveller = this.additionalTravellers.some(
      traveller => traveller.age === null
    );

    if (invalidTraveller) {
      this.errorMessage =
        'Please enter a valid date of birth for every traveller.';
      return;
    }

    if (
      this.formData.travelEndDate < this.formData.travelStartDate
    ) {
      this.errorMessage =
        'Travel end date must be on or after the travel start date.';
      return;
    }

    this.formData.travellers = 1 + this.additionalTravellers.length;

    this.errorMessage =
      'Validation successful! Backend submission is not connected yet.';
  }
}
