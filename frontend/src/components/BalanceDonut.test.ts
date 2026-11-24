import { describe, expect, it } from 'vitest';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { BalanceDonut, availablePercent, donutArcs } from './BalanceDonut';

function render(balance: number, held: number) {
  const host = document.createElement('div');
  host.innerHTML = renderToStaticMarkup(createElement(BalanceDonut, { testid: 'home-bar', balance, held }));
  const figure = host.querySelector('[data-testid="home-bar"]')!;
  return {
    figure,
    pct: host.querySelector('[data-testid="home-bar-pct"]')!.textContent,
    avail: host.querySelectorAll('.donut-avail').length,
    held: host.querySelectorAll('.donut-held').length,
  };
}

describe('예수금 원 그래프', () => {
  it('그림 설명에 묶인 돈과 출금 가능 금액을 원 단위로 읽어 줌', () => {
    const r = render(1_250_000, 150_000);
    expect(r.figure.getAttribute('role')).toBe('img');
    expect(r.figure.getAttribute('aria-label')).toBe('주문에 묶임 150,000원, 출금 가능 1,100,000원');
    expect(r.pct).toBe('88%');
    expect([r.avail, r.held]).toEqual([1, 1]);
  });

  it('묶인 돈이 0이면 출금 가능 고리만 그리고 100%', () => {
    const r = render(1_250_000, 0);
    expect(r.pct).toBe('100%');
    expect([r.avail, r.held]).toEqual([1, 0]);
  });

  it('전부 묶였으면 묶임 고리만 그리고 0%', () => {
    const r = render(300_000, 300_000);
    expect(r.pct).toBe('0%');
    expect([r.avail, r.held]).toEqual([0, 1]);
  });

  it('잔액이 0이면 고리 없이 "—"', () => {
    const r = render(0, 0);
    expect(r.pct).toBe('—');
    expect([r.avail, r.held]).toEqual([0, 0]);
    expect(r.figure.getAttribute('aria-label')).toBe('주문에 묶임 0원, 출금 가능 0원');
  });

  it('14자리 금액도 쉼표를 넣어 읽어 줌', () => {
    const r = render(12_345_678_901_234, 2_345_678_901_234);
    expect(r.figure.getAttribute('aria-label')).toBe('주문에 묶임 2,345,678,901,234원, 출금 가능 10,000,000,000,000원');
    expect(r.pct).toBe('81%');
  });
});

describe('비율과 고리 길이', () => {
  it('조금이라도 묶였으면 100%로, 조금이라도 남았으면 0%로 올리거나 내리지 않음', () => {
    expect(availablePercent(1_000_000, 1)).toBe(99);
    expect(availablePercent(1_000_000, 999_999)).toBe(1);
  });

  it('아주 작은 묶임도 눈에 보이는 길이로 그림', () => {
    const { avail, held } = donutArcs(12_345_678_901_234, 1);
    expect(held!.length).toBeGreaterThan(0);
    expect(avail!.length).toBeGreaterThan(held!.length);
  });
});
