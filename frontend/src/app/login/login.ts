// import { Component } from '@angular/core';
// import { FormsModule } from '@angular/forms';
// import { Router } from '@angular/router';
// import { HttpClient } from '@angular/common/http';


// @Component({
//   selector: 'app-login',
//   standalone: true,
//   imports: [FormsModule],
//   templateUrl: './login.html',
//   styleUrl: './login.css'
// })
// export class Login {

//   email = '';
//   password = '';
//   rememberMe = false;
//   showPassword = false;

//   constructor(private http: HttpClient, private router: Router) {}

//   togglePassword() {
//     this.showPassword = !this.showPassword;
//   }

//   login() {

//     console.log('Email:', this.email);
//     console.log('Password:', this.password);
//     console.log('Remember Me:', this.rememberMe);

//     // Temporary navigation
//     // Later we will connect this to your Spring Boot login API.
//     this.router.navigate(['/dashboard']);
//   }
// }



import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {

  email = '';
  password = '';
  rememberMe = false;
  showPassword = false;

  // Spring Boot login API
  private apiUrl = 'http://localhost:8080/api/auth/login';

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  login(): void {

    // Check that fields are not empty
    if (!this.email || !this.password) {
      alert('Please enter your email and password.');
      return;
    }

    // Data sent to Spring Boot
    const loginData = {
      email: this.email,
      password: this.password
    };

    console.log('Sending login request...');
    console.log('Email:', this.email);

    this.http.post<any>(this.apiUrl, loginData).subscribe({

      // Backend login successful
      next: (response) => {

        console.log('Login successful:', response);

        // Save JWT token returned by backend
        if (response && response.token) {
          localStorage.setItem('token', response.token);
        }

        // Save email when Remember Me is checked
        if (this.rememberMe) {
          localStorage.setItem('rememberedEmail', this.email);
        } else {
          localStorage.removeItem('rememberedEmail');
        }

        // Open dashboard
        this.router.navigate(['/dashboard']);
      },

      // Backend login failed
      error: (error) => {

        console.error('Login failed:', error);

        if (error.status === 401) {
          alert('Invalid email or password.');
        } else if (error.status === 403) {
          alert('Access denied.');
        } else if (error.status === 0) {
          alert('Cannot connect to the backend. Make sure Spring Boot is running on port 8080.');
        } else {
          alert('Login failed. Please try again.');
        }
      }
    });
  }
}
