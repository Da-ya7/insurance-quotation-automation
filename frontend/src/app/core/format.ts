// Small helpers shared by the staff pages.

export function initials(name: string | null | undefined, fallback = '?'): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return fallback.charAt(0).toUpperCase();
  }
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

const COLORS = ['', 'purple', 'orange', 'green'];

export function avatarColor(seed: number | string): string {
  const n = typeof seed === 'number'
    ? seed
    : [...seed].reduce((sum, ch) => sum + ch.charCodeAt(0), 0);
  return COLORS[Math.abs(n) % COLORS.length];
}

// Backend status (EMAIL_SEND_FAILED) -> readable label.
export function statusLabel(status: string | null | undefined): string {
  if (!status) {
    return '';
  }
  return status
    .toLowerCase()
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

export function money(amount: number | null | undefined, currency = 'INR'): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(amount ?? 0);
}

export function shortDate(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  return new Date(value).toLocaleString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}