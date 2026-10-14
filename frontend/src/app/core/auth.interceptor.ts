import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

// Endpoints that never need a login token.
const PUBLIC_URLS = ['/api/auth/', '/api/public/'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  const isPublic = PUBLIC_URLS.some(prefix => req.url.includes(prefix));
  const token = localStorage.getItem('token');

  const request = !isPublic && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      // Token missing, expired or rejected on a staff API: back to login.
      if (!isPublic && (error.status === 401 || error.status === 403)) {
        localStorage.removeItem('token');
        router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
