// 관리자 화면(S-31~S-35)과 AI 이상징후 화면(AI-01~AI-05)의 골격. 1차 범위 밖이라 화면 구성만 둠
// 백엔드 API가 없어 입력·버튼은 모두 막고 "준비 중"으로 보임. 데이터는 지어내지 않고 빈 표·빈 칸으로 둠
// /ops 첫 화면은 1차 관리자 안내(adminwait-panel)를 그대로 유지함(E-11)

import { NavLink, Outlet, useParams } from 'react-router';
import { Mark } from '../../components/Layout';
import { NotFound, PageHead } from '../../components/ui';
import { useSession } from '../../session/SessionProvider';

type Badge = '2차' | 'AI 제안';

interface Planned {
  id: string;
  title: string;
  badge: Badge;
  purpose: string;
  fields?: string[];
  stats?: string[];
  columns?: string[];
  buttons: string[];
  needs: string[];
  note?: string;
}

/** 주소 조각 → 화면 정의. 내용은 설계_3 3.2.4와 설계_2 F-FDS 계열을 옮김 */
export const PLANNED: Record<string, Planned> = {
  stocks: {
    id: 'S-31',
    title: '종목 관리',
    badge: '2차',
    purpose: '거래할 종목을 등록하고 목록을 봐요',
    fields: ['종목코드(숫자·영문 대문자 6자리)', '종목명', '현재가'],
    columns: ['코드', '이름', '현재가', '미체결 주문 수'],
    buttons: ['종목 등록'],
    needs: ['종목 목록 API(N-03). 등록 API(D1)는 있으나 화면에서 부르는 일은 2차에 붙임'],
  },
  price: {
    id: 'S-32',
    title: '시세 입력·체결 결과',
    badge: '2차',
    purpose: '시세를 넣고 그 시세로 무엇이 체결됐는지 봐요',
    fields: ['새 시세'],
    columns: ['주문', '매수/매도', '주문가', '수량', '새 시세에서 체결'],
    stats: ['체결', '그대로 남음', '실패'],
    buttons: ['시세 저장하고 체결 확인'],
    needs: ['미체결 주문 목록과 체결 결과 API(N-09). 시세 입력(D2)만 있음'],
  },
  recon: {
    id: 'S-33·S-34',
    title: '대사 실행·결과',
    badge: '2차',
    purpose: '장부가 맞는지 R-1~R-15 항목별로 검사하고, 불일치 줄을 누르면 그 자리에서 근거를 펼쳐요',
    stats: ['검사한 계좌', '불일치 항목', '걸린 시간'],
    columns: ['계좌', '항목', '기대값', '실제값', '차이'],
    buttons: ['대사 실행'],
    needs: ['대사 실행 API(N-10)와 불일치 상세 응답'],
  },
  settlement: {
    id: 'S-35',
    title: '결제 배치',
    badge: '2차',
    purpose: '매도 대금 D+2 결제가 돌았는지 보고 직접 돌려요',
    fields: ['기준일'],
    columns: ['실행 시각', '기준일', '방식', '처리 건수', '풀린 금액', '결과'],
    stats: ['결제 대기 건수'],
    buttons: ['수동 실행'],
    needs: ['결제 배치 API(N-11). 결제 대기 금액이 계좌 응답에 생겨야 예수금 막대의 세 번째 구간도 그림'],
  },
  ai: {
    id: 'AI-01',
    title: '이상징후 현황',
    badge: 'AI 제안',
    purpose: '자금·주식 이동 중 사람이 봐야 할 건을 근거와 함께 모아 봐요',
    stats: ['거래 전 보류', '검토 경보', '오늘 판정'],
    buttons: ['조사 대기열 보기'],
    needs: ['경보 저장·조회 API, 규칙 엔진(F-FDS 계열). 지금 백엔드에 없음'],
  },
  'ai-queue': {
    id: 'AI-02',
    title: '조사 대기열',
    badge: 'AI 제안',
    purpose: '위험 신호와 처리 상태로 사건을 골라 봐요',
    columns: ['사건', '유형', '금액', '신호', '상태', '담당'],
    buttons: ['사건 보기'],
    needs: ['사건 목록·배정 API'],
  },
  'ai-case': {
    id: 'AI-03',
    title: '사건 상세',
    badge: 'AI 제안',
    purpose: '보류된 거래의 금액, 계좌, 걸린 규칙과 원장 기록을 한곳에서 봐요',
    stats: ['금액', '보내는 계좌', '받는 계좌', '상태'],
    columns: ['판정 근거(걸린 규칙)', '값'],
    buttons: ['판정하기', '원장 기록 보기'],
    needs: ['사건 상세 API. 요약 문장은 사람이 확인하기 전 참고용으로만 보여야 함'],
  },
  'ai-decision': {
    id: 'AI-04',
    title: '보류 판정',
    badge: 'AI 제안',
    purpose: '근거를 확인하고 보류된 송금을 승인하거나 거절해요. 승인할 때만 원장에 한 번 반영돼요',
    fields: ['판정 사유(필수, 감사 기록에 남음)'],
    buttons: ['송금 승인', '송금 거절'],
    needs: ['거래 전 보류, 승인·거절, 중복 승인 방지, 만료·경합 처리, 고객 알림 API. 보류 중 잔액 처리 규칙이 먼저 정해져야 함'],
  },
  'ai-model': {
    id: 'AI-05',
    title: '모델·규칙 운영',
    badge: 'AI 제안',
    purpose: '규칙 버전과 문턱값, 규칙별 경보 건수, 주입 시나리오 성능을 봐요',
    stats: ['규칙 버전', '보류 기준', '규칙별 경보', '주입 시나리오 성능'],
    buttons: [],
    needs: ['규칙·모델 버전 관리 API'],
    note: '드리프트는 운영 지표로 보여 주지 않아요. 분포를 일부러 바꾼 실험을 할 때만 따로 보여 줘요',
  },
};

const OPS_NAV = [
  { to: '/ops', label: '안내', end: true },
  { to: '/ops/stocks', label: '종목 관리' },
  { to: '/ops/price', label: '시세 입력' },
  { to: '/ops/recon', label: '대사' },
  { to: '/ops/settlement', label: '결제 배치' },
  { to: '/ops/ai', label: 'AI 이상징후', end: true },
  { to: '/ops/ai-queue', label: '조사 대기열' },
  { to: '/ops/ai-case', label: '사건 상세' },
  { to: '/ops/ai-decision', label: '보류 판정' },
  { to: '/ops/ai-model', label: '모델·규칙' },
];

export function OpsLayout() {
  return (
    <div className="shell">
      <header className="side">
        <Mark />
        <nav className="nav" aria-label="관리자 메뉴">
          {OPS_NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end}>
              <span className="nav-label">{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <p className="side-note">관리자 화면은 2차예요. 지금은 화면 구성만 볼 수 있어요</p>
      </header>
      <div className="main">
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** 1차 관리자 안내(설계 3.9). 관리자는 지금 로그아웃 API를 부를 권한이 없어(USER 전용) 브라우저 토큰만 지움 */
export function OpsHome() {
  const { signOut } = useSession();
  return (
    <>
      <PageHead title="관리자" />
      <section className="panel">
        <p data-testid="adminwait-panel">관리자 화면은 준비 중임. 종목 등록과 시세 입력은 아직 화면이 없음</p>
        <div className="actions">
          <button type="button" data-testid="adminwait-logout" onClick={() => signOut('logout')}>
            로그아웃
          </button>
        </div>
      </section>
      <section className="panel">
        <h2>미리 보는 화면</h2>
        <div className="planned-grid">
          {Object.entries(PLANNED).map(([key, p]) => (
            <NavLink key={key} to={`/ops/${key}`} className="tile">
              <span className={`badge ${p.badge === '2차' ? 'badge-later' : 'badge-demo'}`}>{p.badge}</span>
              <strong>{p.title}</strong>
              <span>{p.purpose}</span>
            </NavLink>
          ))}
        </div>
      </section>
    </>
  );
}

export function PlannedPage() {
  const key = useParams().screen ?? '';
  const p = PLANNED[key];
  if (!p) return <NotFound />;
  return (
    <div className="stack" data-testid="ops-planned" data-screen={p.id}>
      <PageHead
        title={p.title}
        sub={`${p.id} · ${p.purpose}`}
        badge={<span className={`badge ${p.badge === '2차' ? 'badge-later' : 'badge-demo'}`}>{p.badge} · 준비 중</span>}
      />
      <p className="banner banner-later" data-testid="ops-planned-notice">
        필요한 서버 기능이 아직 없어 이 화면은 동작하지 않아요. 입력과 버튼은 막아 두었어요
      </p>

      {(p.stats || p.fields || p.buttons.length > 0) && (
        <section className="panel">
          {p.stats && (
            <div className="stat-grid">
              {p.stats.map((s) => (
                <div key={s} className="stat">
                  <span>{s}</span>
                  <b aria-label="값 없음">—</b>
                </div>
              ))}
            </div>
          )}
          {p.fields?.map((f) => (
            <div key={f} className="field">
              <label>{f}</label>
              <div className="field-input">
                <input disabled aria-label={f} />
              </div>
            </div>
          ))}
          {p.buttons.length > 0 && (
            <div className="actions">
              {p.buttons.map((b, i) => (
                <button key={b} type="button" className={i === 0 ? 'primary' : undefined} disabled>
                  {b}
                </button>
              ))}
            </div>
          )}
          {p.note && <p className="hint">{p.note}</p>}
        </section>
      )}

      {p.columns && (
        <section className="panel">
          <div className="ledger-wrap">
            <table className="ledger">
              <thead>
                <tr>
                  {p.columns.map((c) => (
                    <th key={c}>{c}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td colSpan={p.columns.length}>보여 줄 데이터가 없어요</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="panel">
        <h2>붙이려면 필요한 것</h2>
        <ul className="needs">
          {p.needs.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      </section>
    </div>
  );
}
