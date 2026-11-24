// E-03 미체결 주문과 취소(설계 3.13). 수수료 650,000 × 0.015% = 97.5 → 97원

import { expect, test } from '@playwright/test';
import { apiDeposit, closeRedis, createMember, registerStock } from './support/backend';
import { loginUI, shot, toPasswordStep, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-03 시세 70,000원 종목에 65,000원 × 10주 매수 → 미체결로 650,097원 묶임 → 취소하면 풀림', async ({ page }) => {
  const m = await createMember();
  const stock = await registerStock(70000);
  await apiDeposit(m, 1_000_000);
  await loginUI(page, m);

  // 1 종목 검색
  await page.goto('/stocks');
  await page.getByTestId('search-keyword-input').fill(stock.code);
  const searched = waitApi(page, 'GET', '/api/stocks');
  await page.getByTestId('search-submit').click();
  expect((await searched).status()).toBe(200);
  await expect(page.getByTestId('search-row')).toHaveCount(1);
  await expect(page.getByTestId('search-row-price')).toHaveText('70,000');

  // 2 종목 상세 → 매수
  const priced = waitApi(page, 'GET', `/api/stocks/${stock.id}/info`);
  await page.getByTestId('search-row').click();
  expect((await priced).status()).toBe(200);
  await expect(page.getByTestId('stock-price')).toHaveText('70,000');
  await expect(page.getByTestId('stock-name')).toHaveText(stock.name);
  await page.getByTestId('stock-buy').click();

  // 3 주문 입력과 계산 줄
  await page.getByTestId('order-side-buy').click();
  await page.getByTestId('order-account-select').selectOption(String(m.accountId));
  await expect(page.getByTestId('order-orderable')).toHaveText('1,000,000');
  await page.getByTestId('order-price-input').fill('65000');
  await page.getByTestId('order-quantity-input').fill('10');
  await expect(page.getByTestId('order-calc-amount')).toHaveText('650,000');
  await expect(page.getByTestId('order-calc-fee')).toHaveText('97');
  await expect(page.getByTestId('order-calc-hold')).toHaveText('650,097');
  await expect(page.getByTestId('order-calc-after-orderable')).toHaveText('349,903');
  await shot(page, 'e03-order-calc');

  // 4 확인 → 비밀번호 → 주문
  await toPasswordStep(page, 'order');
  await page.getByTestId('order-pw-input').fill(m.accountPassword);
  const bought = waitApi(page, 'POST', '/api/stocks/buy');
  await page.getByTestId('order-submit').click();
  const buyRes = await bought;
  expect(buyRes.status()).toBe(200);
  const order = (await buyRes.json()) as { id: number; status: string };
  expect(order.status).toBe('PENDING');
  await expect(page.getByTestId('order-result-status')).toHaveText('미체결');
  await expect(page.getByTestId('order-result-held')).toHaveText('650,097');

  // 5 계좌 상세
  await page.goto(`/accounts/${m.accountId}`);
  await expect(page.getByTestId('account-balance')).toHaveText('1,000,000');
  await expect(page.getByTestId('account-held')).toHaveText('650,097');
  await expect(page.getByTestId('account-orderable')).toHaveText('349,903');

  // 6 주문 내역 탭 → 취소 확인
  const ordersLoaded = waitApi(page, 'GET', `/api/stocks/${m.accountId}/history`);
  await page.getByTestId('account-tab-orders').click();
  expect((await ordersLoaded).status()).toBe(200);
  const row = page.locator(`[data-testid="orders-row"][data-id="${order.id}"]`);
  await expect(row.getByTestId('orders-row-status')).toHaveText('미체결');
  await row.getByTestId('orders-row-cancel').click();
  await expect(page.getByTestId('orders-cancel-release')).toHaveText('650,097');
  await shot(page, 'e03-cancel-panel');

  // 7 취소
  const cancelled = waitApi(page, 'DELETE', `/api/stocks/${m.accountId}/orders/${order.id}`);
  await page.getByTestId('orders-cancel-submit').click();
  expect((await cancelled).status()).toBe(200);
  await expect(row.getByTestId('orders-row-status')).toHaveText('취소');

  // 8 다시 조회
  const reread = waitApi(page, 'GET', `/api/accounts/${m.accountId}`);
  await page.getByTestId('app-refresh').click();
  expect((await reread).status()).toBe(200);
  await expect(page.getByTestId('account-held')).toHaveText('0');
  await expect(page.getByTestId('account-orderable')).toHaveText('1,000,000');
});
