import { Location } from '@angular/common';
import { Router } from '@angular/router';

// Back arrow: go to the previous page; when the page was opened directly
// (no in-app history) fall back to the dashboard so the arrow never "does nothing".
export function goBackOrHome(location: Location, router: Router): void {
  const navId = (history.state && history.state.navigationId) as number | undefined;
  if (navId && navId > 1) {
    location.back();
  } else {
    router.navigate(['/dashboard']);
  }
}
