// 같은 계좌 집중 시나리오(설계 4.3.5): 계좌 하나에 동시 요청을 몰아넣음. 현실 재현이 아니라 일부러 몰아넣는 값임
// 1단계 출금: 잔액 100만 원 계좌(1001번)에 1만 원 출금 300건을 한꺼번에 보냄 → 성공은 최대 100건이어야 함
// 2단계 주문: 잔액 100만 원 계좌(1002번)에 1만 원 매수 주문 300건(현재가 3만 원이라 체결 안 됨) → 묶이는 금액 10,001원이라 최대 99건
// 3단계 취소: 2단계의 미체결 주문마다 취소를 여러 번 동시에 보냄 → 주문마다 취소 성공은 1건이어야 함
import { Counter } from 'k6/metrics';
import { ACCOUNT_PASSWORD, SUMMARY_TREND_STATS, breakdownThresholds, get, login, send } from './common.js';

const CONCURRENCY = Number(__ENV.CONCURRENCY || 300);
const WITHDRAW_ACCOUNT = 1001;
const ORDER_ACCOUNT = 1002;
const ORDER_STOCK = 20; // 현재가 30,000원

const statusCount = new Counter('status_by_request');

function phase(exec, startTime) {
  return { executor: 'per-vu-iterations', vus: CONCURRENCY, iterations: 1, maxDuration: '2m', startTime, exec };
}

export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: Object.assign(
    breakdownThresholds('hot_withdraw', ['hot_withdraw', 'hot_order', 'hot_cancel']),
    breakdownThresholds('hot_order', []),
    breakdownThresholds('hot_cancel', []),
  ),
  scenarios: {
    hot_withdraw: phase('withdraw', '0s'),
    hot_order: phase('order', '40s'),
    hot_cancel: phase('cancel', '80s'),
  },
};

export function setup() {
  return { withdrawToken: login('01098880001'), orderToken: login('01098880002') };
}

function record(res, name) {
  statusCount.add(1, { name, status: String(res.status) });
}

export function withdraw(data) {
  record(send('POST', data.withdrawToken, '/api/transactions/withdrawal',
    { accountId: WITHDRAW_ACCOUNT, amount: 10000, accountPassword: ACCOUNT_PASSWORD }, 'hot_withdraw'), 'hot_withdraw');
}

export function order(data) {
  record(send('POST', data.orderToken, '/api/stocks/buy',
    { accountId: ORDER_ACCOUNT, stockId: ORDER_STOCK, price: 10000, quantity: 1, accountPassword: ACCOUNT_PASSWORD }, 'hot_order'), 'hot_order');
}

// VU마다 미체결 주문 목록을 읽고 (VU 번호 % 주문 수)번째 주문을 취소함. 주문 하나에 취소가 2~3번 몰림
export function cancel(data) {
  const pending = get(data.orderToken, `/api/stocks/${ORDER_ACCOUNT}/history?status=PENDING`, 'hot_cancel_list').json();
  if (pending.length === 0) return;
  const target = pending[(__VU - 1) % pending.length];
  record(send('DELETE', data.orderToken, `/api/stocks/${ORDER_ACCOUNT}/orders/${target.id}`, null, 'hot_cancel'), 'hot_cancel');
}
