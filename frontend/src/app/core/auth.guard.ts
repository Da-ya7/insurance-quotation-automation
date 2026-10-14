import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

function tokenIsValid(token: string | null): boolean {
  if (!token) {
    return false;
  }
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    // exp is in seconds; if it is missing we let the backend decide.
    return !payload.exp || payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export const authGuard: CanActivateFn = () => {
  const router = inject(Router);

  if (tokenIsValid(localStorage.getItem('token'))) {
    return true;
  }

  localStorage.removeItem('token');
  return router.createUrlTree(['/login']);
};
