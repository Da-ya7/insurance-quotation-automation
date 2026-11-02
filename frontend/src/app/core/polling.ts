import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, merge, of, switchMap, timer } from 'rxjs';
import { errorMessage } from './http-error';

export interface Poller {
  /** Fetch right now (e.g. after a filter change or on the refresh button). */
  refresh(): void;
}

/**
 * Calls `fetch` immediately, then every `intervalMs` (default 30 s) and whenever
 * `refresh()` is called. A newer request cancels an older in-flight one.
 * Stops automatically when the calling component is destroyed.
 * Must be called in an injection context (constructor / field initialiser).
 */
export function startPolling<T>(
  fetch: () => Observable<T>,
  onData: (data: T) => void,
  onError: (message: string) => void,
  intervalMs = 30000
): Poller {
  const destroyRef = inject(DestroyRef);
  const manual = new Subject<void>();

  merge(timer(0, intervalMs), manual)
    .pipe(
      switchMap(() =>
        fetch().pipe(
          catchError(err => {
            onError(errorMessage(err));
            return of(undefined as T | undefined);
          })
        )
      ),
      takeUntilDestroyed(destroyRef)
    )
    .subscribe(result => {
      if (result !== undefined) {
        onData(result as T);
      }
    });

  return { refresh: () => manual.next() };
}
