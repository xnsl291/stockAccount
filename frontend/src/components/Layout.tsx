// 회원 화면 공통 틀: 메뉴, 회원 이름, 기준시각, 다시 조회, 화면 설정(잔액 숨기기·큰 글씨·로그아웃), 상담하기(설계 3.10.1, 3.15)
// data-testid와 버튼 글자는 명세 그대로 둠. 다시 조회는 기준시각 바로 옆에 두어 메뉴를 열지 않고 누를 수 있게 함

import { NavLink, Outlet } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useQueries } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { getOrders, logout } from '../api/endpoints';
import { activeAccounts, useAccounts, useMember } from '../hooks/queries';
import { formatAsOf } from '../lib/format';
import { useAsOf } from '../session/asOf';
import { useMemberId, useSession } from '../session/SessionProvider';
import { DisplayMenu } from './DisplayMenu';
import { IconHome, IconLedger, IconList, IconPie, IconRefresh, IconSearch, IconUser, IconWallet } from './icons';
import { SupportChat } from './SupportChat';

const NAV: { to: string; label: string; testid: string; end?: boolean; icon: ReactNode }[] = [
  { to: '/', label: '홈', testid: 'app-nav-home', end: true, icon: <IconHome className="nav-icon" /> },
  { to: '/accounts', label: '계좌', testid: 'app-nav-accounts', icon: <IconWallet className="nav-icon" /> },
  { to: '/stocks', label: '종목 검색', testid: 'app-nav-stocks', icon: <IconSearch className="nav-icon" /> },
  { to: '/orders', label: '주문 내역', testid: 'app-nav-orders', icon: <IconList className="nav-icon" /> },
  { to: '/holdings', label: '보유 주식', testid: 'app-nav-holdings', icon: <IconPie className="nav-icon" /> },
  { to: '/me', label: '내 정보', testid: 'app-nav-me', icon: <IconUser className="nav-icon" /> },
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

export function Mark() {
  return (
    <a className="mark" href="/">
      <span className="mark-symbol">
        <IconLedger />
      </span>
      StockAccount
    </a>
  );
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
        <Mark />
        <nav className="nav" aria-label="주 메뉴">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} data-testid={n.testid}>
              {n.icon}
              <span className="nav-label">{n.label}</span>
              {n.testid === 'app-nav-orders' && pending ? (
                <span className="count">
                  <span className="sr-only">미체결 </span>
                  {pending}
                </span>
              ) : null}
            </NavLink>
          ))}
        </nav>
        <p className="side-note">원장 서버에서 읽은 값만 보여 줘요. 화면 계산 값에는 "예상"을 붙여요.</p>
      </header>
      <div className="main">
        <div className="topbar">
          <div className="who">
            <span className="avatar">
              <IconUser />
            </span>
            <b className="who-name" data-testid="app-user-name">
              {member.data?.name ?? ''}
            </b>
          </div>
          <div className="asof-group">
            <span className="asof" data-testid="app-as-of">
              {asOf ? formatAsOf(asOf) : ''}
            </span>
            <button type="button" className="small refresh" data-testid="app-refresh" onClick={() => void queryClient.invalidateQueries()}>
              <IconRefresh />
              다시 조회
            </button>
          </div>
          <div className="topbar-right">
            <DisplayMenu onLogout={() => void onLogout()} />
          </div>
        </div>
        <main className="content">
          <Outlet />
        </main>
      </div>
      <SupportChat />
    </div>
  );
}
