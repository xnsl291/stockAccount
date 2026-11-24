// E2E 준비 코드: 화면을 거치지 않고 API로 회원·종목·입금을 만들고, 인증번호는 E2E 전용 Redis에서 읽음(설계 3.4.1 방법 1)
// 앱 코드에는 인증번호를 내주는 길을 만들지 않음

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Redis } from 'ioredis';
import { E2E } from '../config.mjs';

export const BACKEND = `http://localhost:${E2E.backendPort}`;
export const ADMIN_FILE = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '.auth', 'admin.json');

let redis: Redis | null = null;
function client(): Redis {
  redis ??= new Redis({ host: 'localhost', port: E2E.redisPort, lazyConnect: false, maxRetriesPerRequest: 3 });
  return redis;
}

export async function closeRedis(): Promise<void> {
  if (redis) await redis.quit();
  redis = null;
}

/**
 * 같은 IP 10분 10번 발송 한도를 비움. 화면·준비 코드가 인증번호를 보내기 직전마다 부름.
 * 브라우저 요청은 프록시를 거쳐 모두 같은 주소(루프백)로 들어와서 한도에 금방 걸림. E2E 전용 Redis에서만 함
 */
export async function clearSmsIpLimit(): Promise<void> {
  const r = client();
  const keys = await r.keys('SMS_SEND_IP:*');
  if (keys.length) await r.del(...keys);
}

/** 서버는 숫자만 남긴 번호로 저장함(PhoneNumber.normalize). 발송 직후 값이 아직 없을 수 있어 몇 번 다시 읽음 */
export async function readSmsCode(phone: string): Promise<string> {
  const key = `SMS:${phone.replace(/\D/g, '')}`;
  for (let i = 0; i < 20; i++) {
    const code = await client().get(key);
    if (code) return code;
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(`인증번호가 Redis에 없음: ${key}`);
}

let lastPhone = 0;
/** 시나리오마다 새 번호: 010 + 작업자 번호 1자리 + 시각 기반 7자리(겹치지 않게 늘 커짐) */
export function newPhone(): string {
  const worker = Number(process.env.TEST_WORKER_INDEX ?? 0) % 10;
  lastPhone = Math.max(lastPhone + 1, Date.now() % 10_000_000);
  return `010${worker}${String(lastPhone).padStart(7, '0')}`;
}

export function newName(prefix = '회원'): string {
  return `${prefix}${Math.floor(Math.random() * 1e6)}`.slice(0, 10);
}

/** 영문 대문자·숫자 6자리. 종목을 시나리오끼리 나눠 써야 한 시나리오의 시세 입력이 다른 시나리오 주문을 체결시키지 않음 */
export function newStockCode(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
}

async function call<T>(method: string, url: string, body?: unknown, token?: string, headers: Record<string, string> = {}): Promise<T> {
  const res = await fetch(`${BACKEND}${url}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${url} → ${res.status} ${text}`);
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
}

export interface TestMember {
  name: string;
  phone: string;
  password: string;
  accountPassword: string;
  accessToken: string;
  memberId: number;
  accountId: number;
  accountNumber: string;
}

/** 문자 인증(A1·A2) → 가입(A3) → 로그인(A4) → 첫 계좌 조회 */
export async function createMember(opts: { name?: string } = {}): Promise<TestMember> {
  const phone = newPhone();
  const name = opts.name ?? newName();
  const password = 'login-pw-1';
  const accountPassword = 'acct-pw-1';
  await clearSmsIpLimit();
  await call('POST', '/api/auth/send-code', { phoneNumber: phone });
  const code = await readSmsCode(phone);
  await call('POST', '/api/auth/verify-phone', { phoneNumber: phone, verificationCode: code, purpose: 'SIGN_UP' });
  await call('POST', '/api/auth/sign-up', { name, phoneNumber: phone, loginPassword: password, initialAccountPassword: accountPassword });
  const tokens = await login(phone, password);
  const accounts = await call<{ id: number; accountNumber: string }[]>('GET', '/api/accounts', undefined, tokens.accessToken);
  const memberId = Number(JSON.parse(Buffer.from(tokens.accessToken.split('.')[1], 'base64url').toString()).sub);
  return { name, phone, password, accountPassword, accessToken: tokens.accessToken, memberId, accountId: accounts[0].id, accountNumber: accounts[0].accountNumber };
}

export function login(phone: string, password: string) {
  return call<{ accessToken: string; refreshToken: string }>('POST', '/api/auth/login', { phoneNumber: phone, password });
}

export async function apiDeposit(member: TestMember, amount: number): Promise<void> {
  await call('POST', '/api/transactions/deposit', { accountId: member.accountId, amount, memo: '준비' }, member.accessToken, {
    'Idempotency-Key': crypto.randomUUID(),
  });
}

export async function getAccount(member: TestMember) {
  return call<{ balance: number; heldAmount: number; availableBalance: number; status: string }>(
    'GET',
    `/api/accounts/${member.accountId}`,
    undefined,
    member.accessToken,
  );
}

async function adminToken(): Promise<string> {
  const admin = JSON.parse(fs.readFileSync(ADMIN_FILE, 'utf-8')) as { phone: string; password: string };
  return (await login(admin.phone, admin.password)).accessToken;
}

/** 관리자 종목 등록(D1). 코드가 겹치면 새 코드로 다시 */
export async function registerStock(price: number, name = '테스트종목'): Promise<{ id: number; code: string; name: string }> {
  const token = await adminToken();
  for (let i = 0; i < 5; i++) {
    try {
      return await call('POST', '/admin/stocks', { code: newStockCode(), name, currentPrice: price }, token);
    } catch (e) {
      if (!String(e).includes('DUPLICATED_STOCK_CODE')) throw e;
    }
  }
  throw new Error('종목코드가 계속 겹침');
}

/** 관리자 시세 입력(D2). 1차는 관리자 화면이 없어 준비 코드가 대신함 */
export async function setStockPrice(stockId: number, price: number): Promise<void> {
  await call('PUT', `/admin/stocks/${stockId}/price`, { price }, await adminToken());
}
