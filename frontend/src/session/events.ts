// API 층(리액트 밖)이 "세션이 끝났음"을 화면 층에 알리는 통로

type Listener = () => void;
const listeners = new Set<Listener>();

export function emitSessionExpired(): void {
  listeners.forEach((l) => l());
}

export function onSessionExpired(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
