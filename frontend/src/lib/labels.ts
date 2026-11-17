// 서버 영문 값 → 화면 한글 이름(설계 3.10.0)

import type { Account, AccountStatus, OrderStatus, TradeType, TransactionType } from '../api/endpoints';

export const ACCOUNT_STATUS: Record<AccountStatus, string> = {
  EXISTS: '정상',
  PENDING: '거래정지',
  DELETED: '해지',
};

export const TX_TYPE: Record<TransactionType, string> = {
  DEPOSIT: '입금',
  WITHDRAW: '출금',
  TRANSFER_OUT: '보낸 송금',
  TRANSFER_IN: '받은 송금',
  STOCK_BUY: '매수',
  STOCK_SELL: '매도',
  FEE: '수수료',
};

export const ORDER_STATUS: Record<OrderStatus, string> = {
  PENDING: '미체결',
  FILLED: '체결',
  CANCELLED: '취소',
};

export function tradeSide(type: TradeType, quantity: number): string {
  if (type === 'BUY') return '매수';
  if (type === 'SELL') return '매도';
  return quantity < 0 ? '이체 보냄' : '이체 받음';
}

/** "별칭 · 계좌번호". 별칭이 없으면 계좌번호만 */
export function accountLabel(a: Pick<Account, 'nickname' | 'accountNumber'>): string {
  return a.nickname ? `${a.nickname} · ${a.accountNumber}` : a.accountNumber;
}
