// Role / user helpers. Values are stored at login (see Login component).

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function currentRole(): string {
  return read('role') ?? '';
}

export function isAdmin(): boolean {
  return currentRole().toUpperCase() === 'ADMIN';
}

export function currentUserName(): string {
  return read('userName') ?? '';
}

export function storeSession(response: { token?: string; role?: string; name?: string }): void {
  if (response.token) {
    localStorage.setItem('token', response.token);
  }
  localStorage.setItem('role', response.role ?? '');
  localStorage.setItem('userName', response.name ?? '');
}

export function clearSession(): void {
  ['token', 'authToken', 'isLoggedIn', 'role', 'userName'].forEach(k => localStorage.removeItem(k));
}
