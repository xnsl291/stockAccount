// 서버 값을 마지막으로 읽은 시각(머리글 기준시각). 조회가 성공할 때마다 갱신함

import { useSyncExternalStore } from 'react';
import type { QueryClient } from '@tanstack/react-query';

let asOf: Date | null = null;
const listeners = new Set<() => void>();

export function trackAsOf(queryClient: QueryClient): () => void {
  return queryClient.getQueryCache().subscribe((event) => {
    if (event.type === 'updated' && event.action.type === 'success') {
      asOf = new Date();
      listeners.forEach((l) => l());
    }
  });
}

export function useAsOf(): Date | null {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => asOf,
  );
}
