// E-04 시세 입력으로 체결(제안 시나리오). 관리자 시세 입력 화면이 1차에 없어 준비 코드가 D2 API로 대신함
// 체결가 64,000 × 10 = 640,000, 수수료 96원 → 잔액 1,000,000 − 640,000 − 96 = 359,904

import { expect, test } from '@playwright/test';
import { apiDeposit, closeRedis, createMember, registerStock, setStockPrice } from './support/backend';
import { MINUS, loginUI, shot, toPasswordStep, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-04 미체결 매수 뒤 관리자가 시세를 64,000원으로 넣으면 체결되고 보유 주식이 생김', async ({ page }) => {
  const m = await createMember();
  const stock = await registerStock(70000);
  await apiDeposit(m, 1_000_000);
  await loginUI(page, m);

  // 1 미체결 매수 주문(이 시나리오 안에서 직접 만듦)
  await page.goto(`/order?stockId=${stock.id}&side=buy&accountId=${m.accountId}`);
  await page.getByTestId('order-price-input').fill('65000');
  await page.getByTestId('order-quantity-input').fill('10');
  await toPasswordStep(page, 'order');
  await page.getByTestId('order-pw-input').fill(m.accountPassword);
  const bought = waitApi(page, 'POST', '/api/stocks/buy');
  await page.getByTestId('order-submit').click();
  const buyRes = await bought;
  expect(buyRes.status()).toBe(200);
  const order = (await buyRes.json()) as { id: number };
  await expect(page.getByTestId('order-result-status')).toHaveText('미체결');

  // 2 관리자 시세 입력(API)
  await setStockPrice(stock.id, 64000);

  // 3 주문 내역 다시 조회 → 체결
  await page.goto(`/orders?accountId=${m.accountId}`);
  const row = page.locator(`[data-testid="orders-row"][data-id="${order.id}"]`);
  await expect(row).toBeVisible();
  const reread = waitApi(page, 'GET', `/api/stocks/${m.accountId}/history`);
  await page.getByTestId('app-refresh').click();
  expect((await reread).status()).toBe(200);
  await expect(row.getByTestId('orders-row-status')).toHaveText('체결');
  await expect(row.getByTestId('orders-row-filled-price')).toHaveText('64,000');

  // 4 계좌 상세 거래내역: 같은 시각이면 나중에 기록된 수수료가 위
  await page.goto(`/accounts/${m.accountId}`);
  const history = waitApi(page, 'GET', `/api/transactions/${m.accountId}/history`);
  await page.getByTestId('account-tab-tx').click();
  expect((await history).status()).toBe(200);
  await expect(page.getByTestId('account-balance')).toHaveText('359,904');
  await expect(page.getByTestId('account-held')).toHaveText('0');
  const rows = page.getByTestId('tx-row');
  await expect(rows.nth(0).getByTestId('tx-row-type')).toHaveText('수수료');
  await expect(rows.nth(0).getByTestId('tx-row-amount')).toHaveText(`${MINUS}96`);
  await expect(rows.nth(0).getByTestId('tx-row-balance-after')).toHaveText('359,904');
  await expect(rows.nth(1).getByTestId('tx-row-type')).toHaveText('매수');
  await expect(rows.nth(1).getByTestId('tx-row-amount')).toHaveText(`${MINUS}640,000`);
  await expect(rows.nth(1).getByTestId('tx-row-balance-after')).toHaveText('360,000');

  // 5 보유 주식 탭
  const holdings = waitApi(page, 'GET', `/api/stocks/${m.accountId}/balance`);
  await page.getByTestId('account-tab-holdings').click();
  expect((await holdings).status()).toBe(200);
  const h = page.locator(`[data-testid="holdings-row"][data-id="${stock.id}"]`);
  await expect(h.getByTestId('holdings-row-quantity')).toHaveText('10');
  await expect(h.getByTestId('holdings-row-avg-price')).toHaveText('64,000');
  await expect(h.getByTestId('holdings-row-price')).toHaveText('64,000');
  await shot(page, 'e04-holdings-after-fill');
});
