// 로그인 상태. 토큰은 localStorage에 있고, 여기서는 토큰에서 읽은 회원 ID·역할만 들고 있음
// 세션이 끝나는 경우(재발급 실패, INVALID_TOKEN, 다른 탭의 로그아웃)를 한곳에서 받아 로그인 화면으로 보냄

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { tokenStore } from '../api/endpoints';
import { ACCESS_KEY, REFRESH_KEY, readClaims, type AccessClaims, type TokenPair } from '../lib/tokens';
import { onSessionExpired } from './events';

export type LoginNotice = 'signup' | 'session' | 'logout' | 'withdrawn' | 'password';

export interface LoginState {
  notice?: LoginNotice;
  from?: string;
}

interface Session {
  claims: AccessClaims | null;
  signIn(tokens: TokenPair): AccessClaims | null;
  /** 브라우저 토큰을 지우고 로그인 화면으로 보냄. 서버 로그아웃(A6)은 부르는 쪽이 먼저 함 */
  signOut(notice: LoginNotice): void;
}

const SessionContext = createContext<Session | null>(null);

const PUBLIC_PATHS = ['/login', '/signup'];

// 로그인 화면에 넘길 사유. claims를 비우면 회원 화면의 문지기(RequireUser)가 먼저 로그인 화면으로 보내면서
// 주소 상태(state)를 자기 것으로 덮어쓸 수 있어, 사유는 따로 한 번만 넘김
let pendingNotice: LoginNotice | null = null;

export function peekLoginNotice(): LoginNotice | null {
  return pendingNotice;
}

export function clearLoginNotice(): void {
  pendingNotice = null;
}

function currentClaims(): AccessClaims | null {
  const tokens = tokenStore.get();
  return tokens ? readClaims(tokens.accessToken) : null;
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [claims, setClaims] = useState<AccessClaims | null>(currentClaims);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const signOut = useCallback(
    (notice: LoginNotice) => {
      pendingNotice = notice;
      tokenStore.clear();
      setClaims(null);
      queryClient.clear();
      navigate('/login', { replace: true, state: { notice } satisfies LoginState });
    },
    [navigate, queryClient],
  );

  // 세션 만료: 원래 주소를 기억해 로그인 뒤 돌려보냄
  useEffect(() => {
    return onSessionExpired(() => {
      setClaims(null);
      queryClient.clear();
      if (PUBLIC_PATHS.includes(window.location.pathname)) return;
      pendingNotice = 'session';
      const from = window.location.pathname + window.location.search;
      navigate('/login', { replace: true, state: { notice: 'session', from } satisfies LoginState });
    });
  }, [navigate, queryClient]);

  // 다른 탭의 로그인·로그아웃 반영(storage 이벤트는 다른 탭에서 바꿨을 때만 옴)
  useEffect(() => {
    function onStorage(e: StorageEvent) {
      if (e.key !== null && e.key !== ACCESS_KEY && e.key !== REFRESH_KEY) return;
      const next = currentClaims();
      setClaims(next);
      if (!next && !PUBLIC_PATHS.includes(window.location.pathname)) {
        pendingNotice = 'session';
        queryClient.clear();
        navigate('/login', { replace: true, state: { notice: 'session' } satisfies LoginState });
      }
    }
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [navigate, queryClient]);

  const value = useMemo<Session>(
    () => ({
      claims,
      signIn(tokens) {
        tokenStore.set(tokens);
        const next = readClaims(tokens.accessToken);
        setClaims(next);
        return next;
      },
      signOut,
    }),
    [claims, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const s = useContext(SessionContext);
  if (!s) throw new Error('SessionProvider 밖에서 useSession을 부름');
  return s;
}

/** 로그인한 회원 ID. 회원 화면(RequireUser 안)에서만 부름 */
export function useMemberId(): number {
  const { claims } = useSession();
  if (!claims) throw new Error('로그인 전에는 회원 ID가 없음');
  return claims.memberId;
}
