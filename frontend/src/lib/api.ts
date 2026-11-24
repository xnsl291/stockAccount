// 모든 API 요청이 지나는 한 곳. 액세스 토큰을 붙이고, 만료(EXPIRED_TOKEN)면 재발급 뒤 원래 요청을 한 번 다시 보냄
// 재발급 규칙(설계 3.5.3)
//  - 탭 안: 재발급 약속(Promise) 하나를 같이 씀. 리프레시 토큰은 한 번 쓰면 서버가 지우므로 각자 부르면 첫 번째만 성공함
//  - 탭 사이: Web Locks로 잠근 뒤 저장소를 다시 읽어, 요청 때 쓴 리프레시 토큰과 다르면 다른 탭이 이미 재발급한 것이라 그 토큰을 씀
//  - 실패로 저장소를 비울 때는 저장소 토큰이 방금 실패한 그 토큰일 때만 비움(늦게 실패한 탭이 새 토큰을 지우지 않게)
//  - 다시 보낸 요청이 또 401이면 재발급하지 않고 세션 만료로 처리(무한 반복 방지)

import type { TokenPair, TokenStore } from './tokens';

export type ApiErrorKind = 'http' | 'network';

export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    readonly status: number,
    readonly code: string,
    readonly errors: Record<string, string> | null = null,
  ) {
    super(code);
    this.name = 'ApiError';
  }
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  /** false면 액세스 토큰을 붙이지 않음(로그인·재발급·문자 인증) */
  auth?: boolean;
}

export interface LockManagerLike {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>;
}

export interface ApiClientDeps {
  fetch: (input: string, init: RequestInit) => Promise<Response>;
  tokens: TokenStore;
  locks?: LockManagerLike | null;
  /** 토큰을 더 쓸 수 없게 됐을 때(재발급 실패, INVALID_TOKEN 등) 부름 */
  onSessionExpired: () => void;
}

export const REFRESH_LOCK = 'stockaccount-token-refresh';
const REISSUE_PATH = '/api/auth/reissue';

export function createApiClient(deps: ApiClientDeps) {
  let inflight: Promise<string> | null = null;

  async function send(path: string, opts: RequestOptions, accessToken: string | null): Promise<Response> {
    const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers };
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    try {
      return await deps.fetch(path, {
        method: opts.method ?? 'GET',
        headers,
        body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      });
    } catch {
      throw new ApiError('network', 0, 'NETWORK_ERROR');
    }
  }

  async function toError(res: Response): Promise<ApiError> {
    let body: { code?: string; errors?: Record<string, string> | null } | null = null;
    try {
      body = await res.json();
    } catch {
      body = null;
    }
    if (!body?.code) {
      // 프록시가 백엔드에 닿지 못한 경우(502·503·504, 본문 없음)는 연결 실패와 같게 다룸
      if (res.status === 502 || res.status === 503 || res.status === 504) return new ApiError('network', res.status, 'NETWORK_ERROR');
      return new ApiError('http', res.status, res.status >= 500 ? 'INTERNAL_SERVER_ERROR' : `HTTP_${res.status}`);
    }
    return new ApiError('http', res.status, body.code, body.errors ?? null);
  }

  async function parseBody(res: Response): Promise<unknown> {
    const text = await res.text();
    if (text === '') return null;
    // 문자열을 돌려주는 API("입금완료" 등)는 요청의 Accept를 따라 Content-Type이 application/json으로 오지만 본문은 따옴표 없는 글자임
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }

  function expire(usedAccess: string | null) {
    const current = deps.tokens.get();
    if (current && usedAccess && current.accessToken !== usedAccess) return; // 그사이 새로 로그인·재발급됨
    deps.tokens.clear();
    deps.onSessionExpired();
  }

  async function reissue(usedRefresh: string): Promise<string> {
    const current = deps.tokens.get();
    if (!current) {
      deps.onSessionExpired();
      throw new ApiError('http', 401, 'INVALID_TOKEN');
    }
    if (current.refreshToken !== usedRefresh) return current.accessToken; // 다른 탭이 먼저 재발급함

    const res = await send(REISSUE_PATH, { method: 'POST', body: { refreshToken: usedRefresh } }, null);
    if (!res.ok) {
      const err = await toError(res);
      if (err.kind === 'network' || err.status >= 500) throw err; // 서버 장애는 토큰을 버릴 이유가 아님
      if (deps.tokens.get()?.refreshToken === usedRefresh) deps.tokens.clear();
      deps.onSessionExpired();
      throw err;
    }
    const tokens = (await res.json()) as TokenPair;
    deps.tokens.set(tokens);
    return tokens.accessToken;
  }

  function refreshAccessToken(usedRefresh: string): Promise<string> {
    if (!inflight) {
      const run = () => reissue(usedRefresh);
      inflight = (deps.locks ? deps.locks.request(REFRESH_LOCK, run) : run()).finally(() => {
        inflight = null;
      });
    }
    return inflight;
  }

  async function request<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
    const useAuth = opts.auth !== false;
    const snapshot = useAuth ? deps.tokens.get() : null;
    let res = await send(path, opts, snapshot?.accessToken ?? null);

    if (res.status === 401 && useAuth) {
      const err = await toError(res);
      if (err.code !== 'EXPIRED_TOKEN' || !snapshot) {
        expire(snapshot?.accessToken ?? null);
        throw err;
      }
      const freshAccess = await refreshAccessToken(snapshot.refreshToken);
      res = await send(path, opts, freshAccess);
      if (res.status === 401) {
        const retryErr = await toError(res);
        expire(freshAccess);
        throw retryErr;
      }
    }
    if (!res.ok) throw await toError(res);
    return (await parseBody(res)) as T;
  }

  return { request };
}

export type ApiClient = ReturnType<typeof createApiClient>;
