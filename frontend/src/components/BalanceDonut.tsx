// 예수금 구성 원 그래프. 출금 가능 = 굵은 초록 고리, 주문에 묶임 = 가는 주황 고리로 그려 색 없이 두께로도 구분함(무늬 없음)
// 금액은 그래프 옆 이름-값 줄(dl)이 보여 주고, 그래프는 비율만 맡음. 가운데 숫자는 출금 가능 비율
// 결제 대기 구간은 D+2 결제(N-07) 뒤에 추가(설계 3.2.5). 외부 차트 라이브러리 없이 SVG로 그림(CSP·번들 크기)

import { formatAmount } from '../lib/format';

const R = 42;
const C = 2 * Math.PI * R;
const MIN_SHARE = 0.03; // 아주 작은 구간도 눈에 보이게 하는 최소 몫
const GAP = 0.012; // 두 구간 사이 틈(둘레 대비)

/** 가운데에 쓸 출금 가능 비율(%). 잔액 0이면 null. 한쪽이라도 남아 있으면 0%·100%로 반올림하지 않음 */
export function availablePercent(balance: number, held: number): number | null {
  if (balance <= 0) return null;
  if (held <= 0) return 100;
  if (held >= balance) return 0;
  return Math.min(99, Math.max(1, Math.floor(((balance - held) / balance) * 100)));
}

/** 두 고리의 길이와 시작점(SVG 둘레 단위). 길이 0인 구간은 그리지 않음 */
export function donutArcs(balance: number, held: number) {
  const rest = Math.max(balance - held, 0);
  if (balance <= 0) return { avail: null, held: null };
  const both = held > 0 && rest > 0;
  const heldShare = both ? Math.min(1 - MIN_SHARE, Math.max(MIN_SHARE, held / balance)) : held > 0 ? 1 : 0;
  const gap = both ? GAP * C : 0;
  return {
    avail: rest > 0 ? { start: gap, length: (1 - heldShare) * C - 2 * gap } : null,
    held: held > 0 ? { start: (1 - heldShare) * C + gap, length: heldShare * C - 2 * gap } : null,
  };
}

export function BalanceDonut({ testid, balance, held }: { testid: string; balance: number; held: number }) {
  const rest = Math.max(balance - held, 0);
  const pct = availablePercent(balance, held);
  const arcs = donutArcs(balance, held);
  const label = `주문에 묶임 ${formatAmount(held)}원, 출금 가능 ${formatAmount(rest)}원`;
  const arc = (a: { start: number; length: number } | null, cls: string, width: number) =>
    a && (
      <circle
        className={cls}
        cx="50"
        cy="50"
        r={R}
        strokeWidth={width}
        strokeDasharray={`${a.length} ${C}`}
        strokeDashoffset={-a.start}
        transform="rotate(-90 50 50)"
      />
    );
  return (
    <figure className="donut" data-testid={testid} aria-label={label} role="img">
      <svg viewBox="0 0 100 100" aria-hidden="true">
        <circle className="donut-track" cx="50" cy="50" r={R} strokeWidth={13} />
        {arc(arcs.avail, 'donut-avail', 13)}
        {arc(arcs.held, 'donut-held', 7)}
      </svg>
      <figcaption className="donut-center" aria-hidden="true">
        <span className="donut-label">출금 가능</span>
        <span className="donut-pct num" data-testid={`${testid}-pct`}>
          {pct === null ? '—' : `${pct}%`}
        </span>
      </figcaption>
    </figure>
  );
}
