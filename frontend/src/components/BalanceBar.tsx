// 예수금 구성 막대. 1차는 두 구간(주문에 묶임 = 점무늬 / 나머지 = 칠함)만 그림. 빗금은 쓰지 않음. 결제 대기 구간은 D+2 결제(N-07) 뒤에 추가(설계 3.2.5)

import { formatAmount } from '../lib/format';

export function BalanceBar({ testid, balance, held }: { testid: string; balance: number; held: number }) {
  const rest = Math.max(balance - held, 0);
  const heldPct = balance > 0 ? (held / balance) * 100 : 0;
  const label = `주문에 묶임 ${formatAmount(held)}원, 출금 가능 ${formatAmount(rest)}원`;
  return (
    <figure className="bar" data-testid={testid} aria-label={label} role="img">
      <div className="bar-track">
        <span className="bar-held" style={{ width: `${heldPct}%` }} />
        <span className="bar-avail" style={{ width: `${100 - heldPct}%` }} />
      </div>
      <figcaption className="bar-legend">
        <span className="legend-held">
          <span className="swatch swatch-held" />
          주문에 묶임 {formatAmount(held)}원
        </span>
        <span className="legend-avail">
          <span className="swatch swatch-avail" />
          출금 가능 {formatAmount(rest)}원
        </span>
      </figcaption>
    </figure>
  );
}
