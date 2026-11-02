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

// ---------------------------------------------------------------------------
// Central status / kind label + colour helpers (used by every page)
// ---------------------------------------------------------------------------

export type StatusGroup = 'PROCESSING' | 'MISSING_DETAILS' | 'COMPLETED' | 'NEEDS_ATTENTION';

export const GROUP_LABELS: Record<string, string> = {
  PROCESSING: 'Processing',
  MISSING_DETAILS: 'Missing Details',
  COMPLETED: 'Quotation Sent',
  NEEDS_ATTENTION: 'Needs Attention'
};

// CSS modifier (see styles.css .x-badge.*) for each status group.
export function groupClass(group: string | null | undefined): string {
  switch (group) {
    case 'COMPLETED': return 'x-green';
    case 'MISSING_DETAILS': return 'x-amber';
    case 'NEEDS_ATTENTION': return 'x-red';
    case 'PROCESSING': return 'x-blue';
    default: return 'x-grey';
  }
}

export function groupLabel(group: string | null | undefined): string {
  return (group && GROUP_LABELS[group]) || statusLabel(group);
}

export const GROUP_COLORS: Record<string, string> = {
  PROCESSING: '#2563eb',
  MISSING_DETAILS: '#d97706',
  COMPLETED: '#16a34a',
  NEEDS_ATTENTION: '#dc2626'
};

export const KIND_LABELS: Record<string, string> = {
  NEW_REQUEST: 'New request',
  CLIENT_REPLY: 'Client reply',
  UNRELATED: 'Unrelated'
};

export function kindLabel(kind: string | null | undefined): string {
  return (kind && KIND_LABELS[kind]) || 'Unclassified';
}

export function kindClass(kind: string | null | undefined): string {
  switch (kind) {
    case 'NEW_REQUEST': return 'x-blue';
    case 'CLIENT_REPLY': return 'x-purple';
    case 'UNRELATED': return 'x-grey';
    default: return 'x-grey';
  }
}

// Email processing status / quotation status / missing-info status colours.
export function simpleStatusClass(status: string | null | undefined): string {
  switch (status) {
    case 'PROCESSED': case 'SENT': case 'RESOLVED': return 'x-green';
    case 'RECEIVED': case 'DRAFT': case 'OPEN': return 'x-amber';
    case 'FAILED': return 'x-red';
    case 'IGNORED': case 'SUPERSEDED': return 'x-grey';
    default: return 'x-grey';
  }
}

export function dateOnly(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? value
    : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
