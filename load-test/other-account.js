// 다른 계좌 영향 시나리오: 계좌 하나(1001번)에 출금 300건을 몰아넣는 동안 다른 회원의 요청이 얼마나 밀리는지 잼
// 몰린 요청이 DB 연결을 모두 쥐고 기다리면, 그 계좌와 상관없는 회원의 요청도 연결을 얻으려고 같이 기다리게 됨
// 다른 계좌 요청: 회원 200명(계좌 1~200번)이 초당 20건(계좌 조회 10, 출금 1만 원 10)을 30초 동안 고르게 보냄
// HOT=0이면 몰아넣기 없이 다른 계좌 요청만 보냄(평소 지연을 보는 대조군)
import { Counter } from 'k6/metrics';
import { ACCOUNT_PASSWORD, SUMMARY_TREND_STATS, breakdownThresholds, get, login, loginAll, send } from './common.js';

const CONCURRENCY = Number(__ENV.CONCURRENCY || 300);
const HOT = __ENV.HOT !== '0';
const USERS = 200;
const WITHDRAW_ACCOUNT = 1001;

const statusCount = new Counter('status_by_request');

const scenarios = {
  other: {
    executor: 'constant-arrival-rate',
    rate: 20,
    timeUnit: '1s',
    duration: '30s',
    preAllocatedVUs: 100,
    maxVUs: 1000, // 요청 하나가 수십 초 밀려도 초당 20건을 계속 보낼 수 있게 넉넉히 둠
    exec: 'other',
  },
};
if (HOT) {
  scenarios.hot_withdraw = { executor: 'per-vu-iterations', vus: CONCURRENCY, iterations: 1, maxDuration: '2m', exec: 'withdraw' };
}

export const options = {
  summaryTrendStats: SUMMARY_TREND_STATS,
  setupTimeout: '5m',
  thresholds: Object.assign(
    breakdownThresholds('other', ['other_read', 'other_withdraw', 'hot_withdraw']),
    HOT ? breakdownThresholds('hot_withdraw', []) : {},
  ),
  scenarios,
};

export function setup() {
  const phones = [];
  for (let i = 1; i <= USERS; i++) phones.push(`010${String(90000000 + i).padStart(8, '0')}`);
  return { withdrawToken: login('01098880001'), users: loginAll(phones) };
}

function record(res, name) {
  statusCount.add(1, { name, status: String(res.status) });
}

export function withdraw(data) {
  record(send('POST', data.withdrawToken, '/api/transactions/withdrawal',
    { accountId: WITHDRAW_ACCOUNT, amount: 10000, accountPassword: ACCOUNT_PASSWORD }, 'hot_withdraw'), 'hot_withdraw');
}

export function other(data) {
  const userIndex = Math.floor(Math.random() * data.users.length);
  const token = data.users[userIndex];
  const accountId = userIndex + 1; // 회원 i+2번의 계좌가 i+1번(seed.sql)
  if (Math.random() < 0.5) {
    record(get(token, `/api/accounts/${accountId}`, 'other_read'), 'other_read');
  } else {
    record(send('POST', token, '/api/transactions/withdrawal',
      { accountId, amount: 10000, accountPassword: ACCOUNT_PASSWORD }, 'other_withdraw'), 'other_withdraw');
  }
}
