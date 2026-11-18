// 평시 시나리오(설계 4.3.5): 초당 100요청, 조회 85 / 주문 5 / 시세 갱신(체결) 5 / 입출금 5
// 합격 기준은 판정하지 않고 기준선으로만 남김. 요청 구성 비율은 설계의 [가정] 값
import { Counter } from 'k6/metrics';
import { ACCOUNT_PASSWORD, SUMMARY_TREND_STATS, breakdownThresholds, get, login, loginAll, send } from './common.js';

const USERS = Number(__ENV.USERS || 200); // 로그인해 둘 일반 회원 수(계좌 1~USERS번)
const ORDER_STOCKS = 5; // 주문·시세 갱신은 1~5번 종목에만 냄(보유 내역이 있는 종목)
const BASE_PRICE = (stockId) => 10000 + stockId * 1000; // seed.sql의 처음 현재가

const statusCount = new Counter('status_by_request');

export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: breakdownThresholds('normal', ['account_detail', 'transaction_history', 'stock_balance', 'stock_price',
    'order_buy', 'order_sell', 'price_update', 'deposit', 'withdrawal']),
  setupTimeout: '5m',
  scenarios: {
    normal: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30m',
      preAllocatedVUs: 200,
      maxVUs: 1500, // 설계의 평시 VU 수. 요청 간격을 고정하고 모자라면 VU를 이만큼까지 늘림
    },
  },
};

export function setup() {
  const phones = [];
  for (let i = 1; i <= USERS; i++) phones.push(`010${String(90000000 + i).padStart(8, '0')}`);
  return { admin: login('01099990000'), users: loginAll(phones) };
}

function pick(n) {
  return Math.floor(Math.random() * n);
}

function record(res, name) {
  statusCount.add(1, { name, status: String(res.status) });
}

export default function (data) {
  const userIndex = pick(data.users.length);
  const token = data.users[userIndex];
  const accountId = userIndex + 1; // 회원 i+2번의 계좌가 i+1번(seed.sql)
  const stockId = 1 + pick(ORDER_STOCKS);
  const r = Math.random();

  if (r < 0.85) {
    const kind = pick(4);
    if (kind === 0) record(get(token, `/api/accounts/${accountId}`, 'account_detail'), 'account_detail');
    else if (kind === 1) record(get(token, `/api/transactions/${accountId}/history?size=20`, 'transaction_history'), 'transaction_history');
    else if (kind === 2) record(get(token, `/api/stocks/${accountId}/balance`, 'stock_balance'), 'stock_balance');
    else record(get(token, `/api/stocks/${1 + pick(20)}`, 'stock_price'), 'stock_price');
  } else if (r < 0.90) {
    // 처음 현재가의 ±3% 안에서 지정가 주문. 시세가 움직이므로 바로 체결되는 주문과 미체결 주문이 섞임
    const type = Math.random() < 0.5 ? 'buy' : 'sell';
    const price = Math.round(BASE_PRICE(stockId) * (0.97 + Math.random() * 0.06));
    const res = send('POST', token, `/api/stocks/${type}`, { accountId, stockId, price, quantity: 1, accountPassword: ACCOUNT_PASSWORD }, `order_${type}`);
    record(res, `order_${type}`);
  } else if (r < 0.95) {
    // 관리자 시세 갱신. 갱신한 종목의 미체결 주문 중 체결되는 것을 이 요청 안에서 체결함
    const price = Math.round(BASE_PRICE(stockId) * (0.95 + Math.random() * 0.10));
    record(send('PUT', data.admin, `/admin/stocks/${stockId}/price`, { price }, 'price_update'), 'price_update');
  } else if (Math.random() < 0.5) {
    record(send('POST', token, '/api/transactions/deposit', { accountId, amount: 10000 }, 'deposit'), 'deposit');
  } else {
    record(send('POST', token, '/api/transactions/withdrawal', { accountId, amount: 10000, accountPassword: ACCOUNT_PASSWORD }, 'withdrawal'), 'withdrawal');
  }
}
