import { describe, expect, it } from 'vitest';
import { buyHoldAmount, maxBuyQuantity, stockTransferFee, tradeFee } from './fee';
import {
  formatAmount,
  formatAmountInput,
  formatDateTime,
  formatPhone,
  formatShortDateTime,
  formatSigned,
  maskPhone,
} from './format';

describe('금액 표시', () => {
  it('천 단위 쉼표, 빼기는 U+2212', () => {
    expect(formatAmount(0)).toBe('0');
    expect(formatAmount(1000000)).toBe('1,000,000');
    expect(formatAmount(-30000)).toBe('−30,000');
    expect(formatAmount(Number.MAX_SAFE_INTEGER)).toBe('9,007,199,254,740,991');
  });

  it('부호 붙인 금액', () => {
    expect(formatSigned(100000)).toBe('+100,000');
    expect(formatSigned(-96)).toBe('−96');
    expect(formatSigned(0)).toBe('0');
  });

  it('입력 칸은 숫자만 남겨 쉼표를 붙임', () => {
    expect(formatAmountInput('100000')).toBe('100,000');
    expect(formatAmountInput('1,0a0')).toBe('100');
    expect(formatAmountInput('007')).toBe('7');
    expect(formatAmountInput('')).toBe('');
  });
});

describe('전화번호·일시', () => {
  it('전화번호 묶기와 가리기', () => {
    expect(formatPhone('01012345678')).toBe('010-1234-5678');
    expect(maskPhone('01012345678')).toBe('010-****-5678');
  });

  it('서버 시각 글자를 시간대 변환 없이 자름', () => {
    expect(formatDateTime('2026-11-17T14:32:05.123')).toBe('2026.11.17 14:32');
    expect(formatShortDateTime('2026-11-17T09:41:00')).toBe('11.17 09:41');
    expect(formatDateTime(null)).toBe('');
  });
});

describe('수수료(서버 FeeRate와 같은 식)', () => {
  it('매매 수수료 0.015% 원 미만 버림 — 설계 E-03·E-04 숫자', () => {
    expect(tradeFee(650000)).toBe(97); // 97.5 → 97
    expect(tradeFee(640000)).toBe(96);
    expect(buyHoldAmount(65000, 10)).toBe(650097);
  });

  it('큰 금액에서도 소수 오차가 없음', () => {
    expect(tradeFee(9_000_000_000_000)).toBe(1_350_000_000);
  });

  it('주식 이체 수수료 1% 원 미만 버림', () => {
    expect(stockTransferFee(70000, 3)).toBe(2100);
    expect(stockTransferFee(199, 1)).toBe(1);
  });

  it('최대 매수 수량은 수수료까지 넣어 주문 가능 금액을 넘지 않음', () => {
    expect(maxBuyQuantity(65000, 1_000_000)).toBe(15);
    expect(buyHoldAmount(65000, 15)).toBeLessThanOrEqual(1_000_000);
    // 650,097원이 정확히 있으면 10주, 1원 모자라면 9주
    expect(maxBuyQuantity(65000, 650097)).toBe(10);
    expect(maxBuyQuantity(65000, 650096)).toBe(9);
    expect(maxBuyQuantity(65000, 0)).toBe(0);
  });
});
