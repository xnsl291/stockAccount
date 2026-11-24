import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient, type LockManagerLike } from './api';
import { createTokenStore, type TokenStore } from './tokens';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const expired = () => json(401, { status: 401, code: 'EXPIRED_TOKEN' });

interface Call {
  path: string;
  auth: string | undefined;
  body: unknown;
}

/** 가짜 서버: 액세스 토큰 "A0"은 만료, 리프레시 토큰은 서버처럼 한 번만 쓸 수 있음 */
function fakeServer(opts: { validRefresh: string; issued?: string[] }) {
  const calls: Call[] = [];
  let validRefresh: string | null = opts.validRefresh;
  let n = 0;
  const fetch = vi.fn(async (path: string, init: RequestInit) => {
    const headers = init.headers as Record<string, string>;
    const body = init.body ? JSON.parse(init.body as string) : undefined;
    calls.push({ path, auth: headers.Authorization, body });
    await new Promise((r) => setTimeout(r, 5)); // 응답이 겹치게 조금 늦춤
    if (path === '/api/auth/reissue') {
      if (body.refreshToken !== validRefresh) return json(401, { code: 'INVALID_TOKEN' });
      n += 1;
      validRefresh = `R${n}`;
      opts.issued?.push(`A${n}`);
      return json(200, { accessToken: `A${n}`, refreshToken: `R${n}` });
    }
    if (headers.Authorization === 'Bearer A0') return expired();
    return json(200, { ok: path });
  });
  return { fetch, calls };
}

function memoryTokens(): TokenStore {
  return createTokenStore(window.localStorage);
}

describe('토큰 재발급 묶기', () => {
  it('한 탭에서 만료 요청 세 개가 겹쳐도 재발급은 한 번만 하고 세 요청 모두 새 토큰으로 다시 보냄', async () => {
    localStorage.clear();
    const tokens = memoryTokens();
    tokens.set({ accessToken: 'A0', refreshToken: 'R0' });
    const server = fakeServer({ validRefresh: 'R0' });
    const client = createApiClient({ fetch: server.fetch, tokens, onSessionExpired: vi.fn() });

    const results = await Promise.all([client.request('/a'), client.request('/b'), client.request('/c')]);

    expect(results).toEqual([{ ok: '/a' }, { ok: '/b' }, { ok: '/c' }]);
    expect(server.calls.filter((c) => c.path === '/api/auth/reissue')).toHaveLength(1);
    expect(server.calls.filter((c) => c.auth === 'Bearer A1')).toHaveLength(3);
    expect(tokens.get()).toEqual({ accessToken: 'A1', refreshToken: 'R1' });
  });

  it('재발급 요청에는 액세스 토큰을 붙이지 않음', async () => {
    localStorage.clear();
    const tokens = memoryTokens();
    tokens.set({ accessToken: 'A0', refreshToken: 'R0' });
    const server = fakeServer({ validRefresh: 'R0' });
    const client = createApiClient({ fetch: server.fetch, tokens, onSessionExpired: vi.fn() });
    await client.request('/a');
    expect(server.calls.find((c) => c.path === '/api/auth/reissue')?.auth).toBeUndefined();
  });

  it('탭 사이: 잠금을 얻었을 때 저장소의 리프레시 토큰이 이미 바뀌었으면 재발급하지 않고 그 토큰을 씀', async () => {
    localStorage.clear();
    const tokens = memoryTokens(); // 두 탭이 같은 localStorage를 봄
    tokens.set({ accessToken: 'A0', refreshToken: 'R0' });
    const server = fakeServer({ validRefresh: 'R0' });

    // 탭 사이 잠금을 흉내 냄: 한 번에 하나만 콜백을 돌림
    let chain: Promise<unknown> = Promise.resolve();
    const locks: LockManagerLike = {
      request<T>(_name: string, cb: () => Promise<T>) {
        const next = chain.then(cb);
        chain = next.catch(() => undefined);
        return next;
      },
    };
    const tabA = createApiClient({ fetch: server.fetch, tokens, locks, onSessionExpired: vi.fn() });
    const tabB = createApiClient({ fetch: server.fetch, tokens, locks, onSessionExpired: vi.fn() });

    const [a, b] = await Promise.all([tabA.request('/a'), tabB.request('/b')]);

    expect(a).toEqual({ ok: '/a' });
    expect(b).toEqual({ ok: '/b' });
    expect(server.calls.filter((c) => c.path === '/api/auth/reissue')).toHaveLength(1);
    expect(tokens.get()).toEqual({ accessToken: 'A1', refreshToken: 'R1' });
  });

  it('재발급이 실패하면 저장소를 비우고 세션 만료를 알림', async () => {
    localStorage.clear();
    const tokens = memoryTokens();
    tokens.set({ accessToken: 'A0', refreshToken: 'R-stolen' });
    const server = fakeServer({ validRefresh: 'R-other' });
    const onSessionExpired = vi.fn();
    const client = createApiClient({ fetch: server.fetch, tokens, onSessionExpired });

    await expect(client.request('/a')).rejects.toMatchObject({ code: 'INVALID_TOKEN' });
    expect(tokens.get()).toBeNull();
    expect(onSessionExpired).toHaveBeenCalled();
  });

  it('늦게 실패한 쪽은 다른 쪽이 막 받아 둔 새 토큰을 지우지 않음', async () => {
    localStorage.clear();
    const tokens = memoryTokens();
    tokens.set({ accessToken: 'A0', refreshToken: 'R0' });
    const fetch = vi.fn(async (path: string) => {
      if (path === '/api/auth/reissue') {
        tokens.set({ accessToken: 'A9', refreshToken: 'R9' }); // 응답을 기다리는 사이 다른 탭이 새 토큰을 받음
        return json(401, { code: 'INVALID_TOKEN' });
      }
      return expired();
    });
    const client = createApiClient({ fetch, tokens, onSessionExpired: vi.fn() });

    await expect(client.request('/a')).rejects.toBeInstanceOf(ApiError);
    expect(tokens.get()).toEqual({ accessToken: 'A9', refreshToken: 'R9' });
  });

  it('INVALID_TOKEN이면 재발급하지 않고 바로 세션 만료', async () => {
    localStorage.clear();
    const tokens = memoryTokens();
    tokens.set({ accessToken: 'X', refreshToken: 'R0' });
    const fetch = vi.fn(async () => json(401, { code: 'INVALID_TOKEN' }));
    const onSessionExpired = vi.fn();
    const client = createApiClient({ fetch, tokens, onSessionExpired });

    await expect(client.request('/a')).rejects.toMatchObject({ code: 'INVALID_TOKEN' });
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(tokens.get()).toBeNull();
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
  });

  it('재발급 뒤 다시 보낸 요청도 401이면 더 재발급하지 않음(무한 반복 방지)', async () => {
    localStorage.clear();
    const tokens = memoryTokens();
    tokens.set({ accessToken: 'A0', refreshToken: 'R0' });
    const fetch = vi.fn(async (path: string) =>
      path === '/api/auth/reissue' ? json(200, { accessToken: 'A1', refreshToken: 'R1' }) : expired(),
    );
    const onSessionExpired = vi.fn();
    const client = createApiClient({ fetch, tokens, onSessionExpired });

    await expect(client.request('/a')).rejects.toMatchObject({ code: 'EXPIRED_TOKEN' });
    expect(fetch).toHaveBeenCalledTimes(3); // 원 요청, 재발급, 다시 보낸 요청
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
  });

  it('연결 실패는 network 오류로 바꾸고 토큰은 그대로 둠', async () => {
    localStorage.clear();
    const tokens = memoryTokens();
    tokens.set({ accessToken: 'A1', refreshToken: 'R1' });
    const fetch = vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    });
    const client = createApiClient({ fetch, tokens, onSessionExpired: vi.fn() });

    await expect(client.request('/a')).rejects.toMatchObject({ kind: 'network' });
    expect(tokens.get()).not.toBeNull();
  });

  it('문자열 응답은 글자 그대로 돌려줌', async () => {
    localStorage.clear();
    const fetch = vi.fn(async () => new Response('출금완료', { status: 200, headers: { 'Content-Type': 'text/plain' } }));
    const client = createApiClient({ fetch, tokens: memoryTokens(), onSessionExpired: vi.fn() });
    await expect(client.request('/x', { auth: false })).resolves.toBe('출금완료');
  });

  it('Content-Type이 json인데 본문이 따옴표 없는 글자여도 글자로 돌려줌(스프링 String 응답)', async () => {
    localStorage.clear();
    const fetch = vi.fn(async () => new Response('인증 메세지 발송 완료', { status: 200, headers: { 'Content-Type': 'application/json' } }));
    const client = createApiClient({ fetch, tokens: memoryTokens(), onSessionExpired: vi.fn() });
    await expect(client.request('/x', { auth: false })).resolves.toBe('인증 메세지 발송 완료');
  });
});
