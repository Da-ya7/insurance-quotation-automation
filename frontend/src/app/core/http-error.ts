import { HttpErrorResponse } from '@angular/common/http';

// Turns any HTTP failure into a message that can be shown to the user.
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'Cannot reach server';
  }
  if (error.status === 401) {
    return 'Your session has expired. Please log in again.';
  }
  if (error.status === 403) {
    return 'You do not have permission';
  }
  const body = error.error;
  if (typeof body === 'string' && body.trim()) {
    // Plain-text body (e.g. 409 conflict) or a JSON string.
    try {
      const parsed = JSON.parse(body);
      if (parsed && typeof parsed.message === 'string') {
        return parsed.message;
      }
    } catch { /* plain text */ }
    return body.length > 300 ? fallback : body;
  }
  if (body && typeof body === 'object') {
    if (typeof body.message === 'string' && body.message) {
      const details: string[] = Array.isArray(body.errors) ? body.errors : [];
      return body.message + (details.length ? ' ' + details.join('; ') : '');
    }
    if (typeof body.error === 'string' && body.error) {
      return body.error;
    }
  }
  return fallback;
}
