// 토큰 저장소. 1차는 두 토큰 모두 localStorage에 둠(설계 3.5.3 대안 가, 사용자 확정)
// 위험: 화면에 악성 스크립트가 끼어들면 두 토큰을 모두 읽을 수 있음. CSP와 외부 스크립트 금지로 줄이고 README 한계에 적음

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export interface TokenStore {
  get(): TokenPair | null;
  set(tokens: TokenPair): void;
  clear(): void;
}

export const ACCESS_KEY = 'stockaccount.accessToken';
export const REFRESH_KEY = 'stockaccount.refreshToken';

export function createTokenStore(storage: Storage): TokenStore {
  return {
    get() {
      const accessToken = storage.getItem(ACCESS_KEY);
      const refreshToken = storage.getItem(REFRESH_KEY);
      return accessToken && refreshToken ? { accessToken, refreshToken } : null;
    },
    set(tokens) {
      storage.setItem(ACCESS_KEY, tokens.accessToken);
      storage.setItem(REFRESH_KEY, tokens.refreshToken);
    },
    clear() {
      storage.removeItem(ACCESS_KEY);
      storage.removeItem(REFRESH_KEY);
    },
  };
}

export interface AccessClaims {
  memberId: number;
  role: string;
}

/** 액세스 토큰 본문에서 회원 ID(sub)와 역할(role)을 꺼냄. 서명은 확인하지 않음(화면 이동 판단용, 권한 판정은 서버가 함) */
export function readClaims(accessToken: string): AccessClaims | null {
  try {
    const payload = accessToken.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const claims = JSON.parse(json) as { sub?: string; role?: string };
    const memberId = Number(claims.sub);
    if (!Number.isInteger(memberId) || !claims.role) return null;
    return { memberId, role: claims.role };
  } catch {
    return null;
  }
}
