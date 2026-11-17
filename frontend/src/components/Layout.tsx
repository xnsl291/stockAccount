// 회원 화면 공통 틀: 머리글 메뉴, 회원 이름, 기준시각, 다시 조회, 로그아웃(설계 3.10.1)
// 새 디자인을 입힐 때 배치를 바꾸는 곳. data-testid와 버튼 글자는 그대로 둠

import { NavLink, Outlet } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useQueries } from '@tanstack/react-query';
import { getOrders, logout } from '../api/endpoints';
import { activeAccounts, useAccounts, useMember } from '../hooks/queries';
import { formatAsOf } from '../lib/format';
import { useAsOf } from '../session/asOf';
import { useMemberId, useSession } from '../session/SessionProvider';

const NAV = [
  { to: '/', label: '홈', testid: 'app-nav-home', end: true },
  { to: '/accounts', label: '계좌', testid: 'app-nav-accounts' },
  { to: '/stocks', label: '종목 검색', testid: 'app-nav-stocks' },
  { to: '/orders', label: '주문 내역', testid: 'app-nav-orders' },
  { to: '/holdings', label: '보유 주식', testid: 'app-nav-holdings' },
  { to: '/me', label: '내 정보', testid: 'app-nav-me' },
];

/** 메뉴의 미체결 건수 배지: 계좌마다 미체결 주문(K7 status=PENDING)을 세어 더함 */
function usePendingCount(): number | null {
  const accounts = activeAccounts(useAccounts().data);
  return useQueries({
    queries: accounts.map((a) => ({
      queryKey: ['orders', a.id, { status: 'PENDING' }],
      queryFn: () => getOrders(a.id, { status: 'PENDING' }),
    })),
    combine: (rs) => (rs.every((r) => r.data) ? rs.reduce((n, r) => n + (r.data?.length ?? 0), 0) : null),
  });
}

export function Layout() {
  const memberId = useMemberId();
  const member = useMember(memberId);
  const asOf = useAsOf();
  const queryClient = useQueryClient();
  const { signOut } = useSession();
  const pending = usePendingCount();

  async function onLogout() {
    try {
      await logout();
    } catch {
      // 서버 로그아웃이 실패해도 이 브라우저의 토큰은 지움
    }
    signOut('logout');
  }

  return (
    <div className="shell">
      <header className="side">
        <a className="mark" href="/">
          <b>StockAccount</b>
        </a>
        <nav className="nav" aria-label="주 메뉴">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} data-testid={n.testid}>
              {n.label}
              {n.testid === 'app-nav-orders' && pending ? <span className="count">미체결 {pending}</span> : null}
            </NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <b data-testid="app-user-name">{member.data?.name ?? ''}</b>
          <button type="button" data-testid="app-logout" onClick={() => void onLogout()}>
            로그아웃
          </button>
        </div>
      </header>
      <div className="main">
        <div className="topbar">
          <span className="asof" data-testid="app-as-of">
            {asOf ? formatAsOf(asOf) : ''}
          </span>
          <button type="button" data-testid="app-refresh" onClick={() => void queryClient.invalidateQueries()}>
            다시 조회
          </button>
        </div>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
