// 두 시나리오가 같이 쓰는 로그인·요청 도구
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
export const PASSWORD = 'password123'; // seed.sql의 로그인 비밀번호
export const ACCOUNT_PASSWORD = '1234'; // seed.sql의 계좌 비밀번호

// 200 말고는 모두 실패로 셈. 잔액 부족 같은 업무 거절(400)도 http_req_failed에 들어가므로
// 시나리오 결과에는 응답 코드별 개수를 따로 남김
http.setResponseCallback(http.expectedStatuses(200));

export function login(phoneNumber) {
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ phoneNumber, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });
  if (res.status !== 200) throw new Error(`로그인 실패 ${phoneNumber}: ${res.status} ${res.body}`);
  return res.json('accessToken');
}

// 여러 명을 20명씩 동시에 로그인시켜 토큰 목록을 만듦. 로그인은 BCrypt 비교라 한 명씩 하면 오래 걸림
export function loginAll(phoneNumbers) {
  const tokens = [];
  for (let i = 0; i < phoneNumbers.length; i += 20) {
    const batch = phoneNumbers.slice(i, i + 20).map((phoneNumber) => ({
      method: 'POST',
      url: `${BASE_URL}/api/auth/login`,
      body: JSON.stringify({ phoneNumber, password: PASSWORD }),
      params: { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
    }));
    for (const res of http.batch(batch)) {
      if (res.status !== 200) throw new Error(`로그인 실패: ${res.status} ${res.body}`);
      tokens.push(res.json('accessToken'));
    }
  }
  return tokens;
}

function idempotencyKey() {
  return `${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2)}`.slice(0, 64);
}

// WARM=1이면 몰아넣는 요청을 보내기 전에 VU마다 연결을 미리 맺어 두고, 시나리오 시작 4초 뒤에 한꺼번에 보냄.
// 수백 개 VU가 한 순간에 새 연결을 맺으면 Docker Desktop의 호스트 연결이 일부를 거절해(connection refused)
// 그 요청은 앱에 닿지도 않은 채 응답 시간 0으로 집계됨. 취소 단계는 목록 조회로 이미 연결을 맺은 뒤 보내 이 문제가 없었음
export const WARM = __ENV.WARM === '1';
export function warmUp(token) {
  if (!WARM) return;
  sleep((__VU % 100) * 0.02); // 미리 맺는 연결도 한꺼번에 몰리지 않게 2초에 걸쳐 나눔
  get(token, '/api/stocks/1', 'warmup');
  const wait = (exec.scenario.startTime + 4000 - Date.now()) / 1000;
  if (wait > 0) sleep(wait);
}

export function get(token, url, name) {
  return http.get(`${BASE_URL}${url}`, { headers: { Authorization: `Bearer ${token}` }, tags: { name } });
}

// 돈이나 주식이 움직이는 요청. 요청마다 새 Idempotency-Key를 붙임
export function send(method, token, url, body, name) {
  return http.request(method, `${BASE_URL}${url}`, body === null ? null : JSON.stringify(body), {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey(),
    },
    tags: { name },
  });
}

export const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'];

// k6 요약은 threshold를 건 세부 지표만 따로 보여 줌. 합격 판정이 아니라 요청 종류별·응답 코드별 숫자를
// 요약 파일에 남기려고 항상 통과하는 threshold를 걺
export function breakdownThresholds(scenario, names) {
  const t = {
    [`http_req_duration{scenario:${scenario}}`]: ['max>=0'],
    [`http_req_failed{scenario:${scenario}}`]: ['rate>=0'],
  };
  for (const name of names) {
    t[`http_req_duration{name:${name}}`] = ['max>=0'];
    for (const status of ['200', '400', '401', '409', '500']) t[`status_by_request{name:${name},status:${status}}`] = ['count>=0'];
  }
  return t;
}
