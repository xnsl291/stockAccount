// E-15 관리자 종목 관리(S-31)·시세 입력(S-32). 시세를 넣으면 서버가 맞는 미체결 주문을 바로 체결하고 결과를 돌려줌
// 관리자는 전체 준비(global-setup)에서 만든 계정을 씀. 종목은 시나리오마다 새로 만들어 다른 시나리오 주문과 섞이지 않게 함

import fs from 'node:fs';
import { expect, test, type Browser, type Page } from '@playwright/test';
import { ADMIN_FILE, apiDeposit, closeRedis, createMember, registerStock, type TestMember } from './support/backend';
import { loginUI, shot, toPasswordStep, waitApi } from './support/ui';

test.afterAll(closeRedis);

const admin = () => JSON.parse(fs.readFileSync(ADMIN_FILE, 'utf-8')) as { phone: string; password: string };

async function adminPage(browser: Browser, baseURL: string | undefined): Promise<Page> {
  const ctx = await browser.newContext({ baseURL, locale: 'ko-KR' });
  const page = await ctx.newPage();
  await loginUI(page, admin(), /\/ops$/);
  return page;
}

/** 회원 화면에서 지정가 매수를 내고 주문 ID를 돌려줌 */
async function buy(page: Page, m: TestMember, stockId: number, price: string, qty: string): Promise<number> {
  await page.goto(`/order?stockId=${stockId}&side=buy&accountId=${m.accountId}`);
  await page.getByTestId('order-price-input').fill(price);
  await page.getByTestId('order-quantity-input').fill(qty);
  await toPasswordStep(page, 'order');
  await page.getByTestId('order-pw-input').fill(m.accountPassword);
  const bought = waitApi(page, 'POST', '/api/stocks/buy');
  await page.getByTestId('order-submit').click();
  const res = await bought;
  expect(res.status()).toBe(200);
  await expect(page.getByTestId('order-result-status')).toHaveText('미체결');
  return ((await res.json()) as { id: number }).id;
}

test('E-15 관리자가 종목 목록에서 미체결 주문을 확인하고 시세를 64,000원으로 넣으면 1건 체결, 회원 주문 내역도 체결', async ({ page, browser, baseURL }) => {
  const m = await createMember();
  const stock = await registerStock(70000, '관리흐름전자');
  await apiDeposit(m, 1_000_000);

  // 1 관리자 로그인 → 종목 관리. 새 종목이 미체결 0건으로 보임
  const ops = await adminPage(browser, baseURL);
  const listed = waitApi(ops, 'GET', '/admin/stocks');
  await ops.getByTestId('adminwait-stocks-link').click();
  expect((await listed).status()).toBe(200);
  await expect(ops).toHaveURL(/\/ops\/stocks$/);
  const row = ops.locator(`[data-testid="opsstocks-row"][data-id="${stock.id}"]`);
  await expect(row.getByTestId('opsstocks-row-name')).toHaveText('관리흐름전자');
  await expect(row.getByTestId('opsstocks-row-code')).toHaveText(stock.code);
  await expect(row.getByTestId('opsstocks-row-price')).toHaveText('70,000');
  await expect(row.getByTestId('opsstocks-row-pending')).toHaveText('0');

  // 2 회원이 65,000원 × 10주 지정가 매수 → 시세 70,000원이라 미체결
  await loginUI(page, m);
  const orderId = await buy(page, m, stock.id, '65000', '10');

  // 3 관리자 종목 목록을 다시 읽으면 미체결 1건, 주문 보기에 그 주문이 보임
  await ops.reload();
  await expect(row.getByTestId('opsstocks-row-pending')).toHaveText('1');
  const orders = waitApi(ops, 'GET', `/admin/stocks/${stock.id}/orders`);
  await row.getByTestId('opsstocks-row-orders-toggle').click();
  expect((await orders).status()).toBe(200);
  const panel = ops.getByTestId('opsstocks-orders-panel');
  const o = panel.locator(`[data-testid="opsstocks-order-row"][data-id="${orderId}"]`);
  await expect(o.getByTestId('opsstocks-order-row-side')).toHaveText('매수');
  await expect(o.getByTestId('opsstocks-order-row-status')).toHaveText('미체결');
  await expect(o.getByTestId('opsstocks-order-row-price')).toHaveText('65,000');
  await expect(o.getByTestId('opsstocks-order-row-quantity')).toHaveText('10');
  await expect(o.getByTestId('opsstocks-order-row-account')).toHaveText(String(m.accountId));
  await shot(ops, 'e15-ops-stocks-orders');

  // 4 시세 입력 화면: 64,000원을 넣으면 그 주문에 "새 시세에서 체결" 표시
  await row.getByTestId('opsstocks-row-price-link').click();
  await expect(ops).toHaveURL(new RegExp(`/ops/price\\?stockId=${stock.id}$`));
  await expect(ops.getByTestId('opsprice-stock-name')).toHaveText('관리흐름전자');
  await expect(ops.getByTestId('opsprice-current-price')).toHaveText('70,000');
  await expect(ops.getByTestId('opsprice-pending-count')).toHaveText('1');
  const pendingRow = ops.locator(`[data-testid="opsprice-order-row"][data-id="${orderId}"]`);
  await expect(pendingRow.getByTestId('opsprice-order-row-will-fill')).toHaveCount(0);
  await ops.getByTestId('opsprice-price-input').fill('64000');
  await expect(pendingRow.getByTestId('opsprice-order-row-will-fill')).toBeVisible();

  // 5 저장 → 체결 1건, 남은 0건, 실패 0건, 체결된 주문 ID
  const put = waitApi(ops, 'PUT', `/admin/stocks/${stock.id}/price`);
  await ops.getByTestId('opsprice-submit').click();
  expect((await put).status()).toBe(200);
  await expect(ops.getByTestId('opsprice-result-filled')).toHaveText('1');
  await expect(ops.getByTestId('opsprice-result-remaining')).toHaveText('0');
  await expect(ops.getByTestId('opsprice-result-failed')).toHaveText('0');
  await expect(ops.getByTestId('opsprice-result-filled-ids')).toHaveText(String(orderId));
  await expect(ops.getByTestId('opsprice-result-price')).toHaveText('64,000');
  await expect(ops.getByTestId('opsprice-current-price')).toHaveText('64,000');
  await expect(ops.getByTestId('opsprice-orders-empty')).toBeVisible();
  await shot(ops, 'e15-ops-price-result');
  await ops.context().close();

  // 6 회원 주문 내역: 체결, 종목 이름은 서버 응답 값
  await page.goto(`/orders?accountId=${m.accountId}`);
  const mine = page.locator(`[data-testid="orders-row"][data-id="${orderId}"]`);
  await expect(mine).toBeVisible();
  const reread = waitApi(page, 'GET', `/api/stocks/${m.accountId}/history`);
  await page.getByTestId('app-refresh').click();
  expect((await reread).status()).toBe(200);
  await expect(mine.getByTestId('orders-row-status')).toHaveText('체결');
  await expect(mine.getByTestId('orders-row-stock')).toHaveText('관리흐름전자');
  await expect(mine.getByTestId('orders-row-filled-price')).toHaveText('64,000');

  // 7 보유 주식: 이름·현재가·평가금액도 서버 응답 값
  await page.goto(`/holdings?accountId=${m.accountId}`);
  const h = page.locator(`[data-testid="holdings-row"][data-id="${stock.id}"]`);
  await expect(h.getByTestId('holdings-row-name')).toHaveText('관리흐름전자');
  await expect(h.getByTestId('holdings-row-code')).toHaveText(stock.code);
  await expect(h.getByTestId('holdings-row-value')).toHaveText('640,000');
});

test('E-15 예외: 조건이 안 맞는 시세면 체결 0건·남은 1건, 잘못된 시세는 화면이 막고, 회원은 관리자 화면에 못 들어감', async ({ page, browser, baseURL }) => {
  const m = await createMember();
  const stock = await registerStock(70000, '예외확인전자');
  await apiDeposit(m, 1_000_000);
  await loginUI(page, m);
  const orderId = await buy(page, m, stock.id, '60000', '1');

  // 1 종목을 고르지 않고 시세 입력으로 오면 고르라는 안내
  const ops = await adminPage(browser, baseURL);
  await ops.goto('/ops/price');
  await expect(ops.getByTestId('opsprice-pick-empty')).toBeVisible();

  // 2 0원은 화면이 막음(요청이 나가지 않음)
  await ops.goto(`/ops/price?stockId=${stock.id}`);
  await expect(ops.getByTestId('opsprice-current-price')).toHaveText('70,000');
  await ops.getByTestId('opsprice-price-input').fill('0');
  await expect(ops.getByTestId('opsprice-price-error')).toBeVisible();
  await expect(ops.getByTestId('opsprice-submit')).toBeDisabled();

  // 3 65,000원: 매수 주문가 60,000원보다 높아 체결되지 않음 → 체결 0건, 남은 1건
  await ops.getByTestId('opsprice-price-input').fill('65000');
  const pendingRow = ops.locator(`[data-testid="opsprice-order-row"][data-id="${orderId}"]`);
  await expect(pendingRow).toBeVisible();
  await expect(pendingRow.getByTestId('opsprice-order-row-will-fill')).toHaveCount(0);
  const put = waitApi(ops, 'PUT', `/admin/stocks/${stock.id}/price`);
  await ops.getByTestId('opsprice-submit').click();
  expect((await put).status()).toBe(200);
  await expect(ops.getByTestId('opsprice-result-filled')).toHaveText('0');
  await expect(ops.getByTestId('opsprice-result-remaining')).toHaveText('1');
  await expect(ops.getByTestId('opsprice-result-failed')).toHaveText('0');
  await expect(ops.getByTestId('opsprice-result-filled-ids')).toHaveText('없음');
  await expect(ops.getByTestId('opsprice-current-price')).toHaveText('65,000');
  await expect(pendingRow.getByTestId('opsprice-order-row-status')).toHaveText('미체결');
  await shot(ops, 'e15-ops-price-no-fill');
  await ops.context().close();

  // 4 회원 주문은 그대로 미체결
  await page.goto(`/orders?accountId=${m.accountId}`);
  await expect(page.locator(`[data-testid="orders-row"][data-id="${orderId}"]`).getByTestId('orders-row-status')).toHaveText('미체결');

  // 5 회원이 관리자 화면 주소로 가면 홈으로, 관리자 API는 403
  await page.goto('/ops/stocks');
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByTestId('home-total-balance')).toBeVisible();
  const token = await page.evaluate(() => localStorage.getItem('stockaccount.accessToken'));
  const res = await page.request.get('/admin/stocks?page=0&size=20', { headers: { Authorization: `Bearer ${token}` } });
  expect(res.status()).toBe(403);
  expect(((await res.json()) as { code: string }).code).toBe('ACCESS_DENIED');
});
