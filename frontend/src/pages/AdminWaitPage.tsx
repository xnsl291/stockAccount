// 1차 관리자 안내(설계 3.9). 관리자 화면은 2차라 안내와 로그아웃만 둠
// 관리자는 지금 로그아웃 API(/api/auth/logout)를 부를 권한이 없어(USER 전용) 브라우저 토큰만 지움. 서버 쪽 토큰은 만료 때까지 살아 있음

import { useSession } from '../session/SessionProvider';

export function AdminWaitPage() {
  const { signOut } = useSession();
  return (
    <main className="auth">
      <h1>관리자</h1>
      <p className="panel" data-testid="adminwait-panel">
        관리자 화면은 준비 중임. 종목 등록과 시세 입력은 아직 화면이 없음
      </p>
      <button type="button" data-testid="adminwait-logout" onClick={() => signOut('logout')}>
        로그아웃
      </button>
    </main>
  );
}
