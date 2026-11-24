// E-02 입금과 출금(설계 3.13). 3단계는 화면 사전 차단(사용자 결정)이라 출금 요청이 나가지 않아야 함

import { expect, test } from '@playwright/test';
import { closeRedis, createMember } from './support/backend';
import { MINUS, loginUI, shot, toPasswordStep, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-02 100,000원 입금 → 30,000원 출금 → 200,000원 출금은 화면이 막음', async ({ page }) => {
  const m = await createMember();
  await loginUI(page, m);

  // 1 입금
  await page.goto('/deposit');
  await page.getByTestId('deposit-account-select').selectOption(String(m.accountId));
  await page.getByTestId('deposit-amount-input').fill('100000');
  await page.getByTestId('deposit-next').click();
  await expect(page.getByTestId('deposit-confirm-amount')).toHaveText('100,000');
  const deposited = waitApi(page, 'POST', '/api/transactions/deposit');
  const reread = waitApi(page, 'GET', `/api/accounts/${m.accountId}`);
  await page.getByTestId('deposit-submit').click();
  expect((await deposited).status()).toBe(200);
  expect((await reread).status()).toBe(200);
  await expect(page.getByTestId('deposit-result-balance')).toHaveText('100,000');

  // 2 출금 30,000
  await page.goto(`/withdraw?accountId=${m.accountId}`);
  await page.getByTestId('withdraw-amount-input').fill('30000');
  await toPasswordStep(page, 'withdraw');
  await page.getByTestId('withdraw-pw-input').fill(m.accountPassword);
  const withdrawn = waitApi(page, 'POST', '/api/transactions/withdrawal');
  await page.getByTestId('withdraw-submit').click();
  expect((await withdrawn).status()).toBe(200);
  await expect(page.getByTestId('withdraw-result-balance')).toHaveText('70,000');

  // 3 출금 200,000 → 화면이 막고 요청이 나가지 않음
  let withdrawCalls = 0;
  page.on('request', (r) => {
    if (r.method() === 'POST' && r.url().endsWith('/api/transactions/withdrawal')) withdrawCalls++;
  });
  await page.goto(`/withdraw?accountId=${m.accountId}`);
  await expect(page.getByTestId('withdraw-withdrawable')).toHaveText('70,000');
  await page.getByTestId('withdraw-amount-input').fill('200000');
  await expect(page.getByTestId('withdraw-amount-error')).toHaveText('출금 가능 금액(70,000원)보다 커요');
  await expect(page.getByTestId('withdraw-next')).toBeDisabled();
  expect(withdrawCalls).toBe(0);

  // 4 거래내역: 최근 순으로 출금, 입금
  await page.goto(`/accounts/${m.accountId}`);
  const history = waitApi(page, 'GET', `/api/transactions/${m.accountId}/history`);
  await page.getByTestId('account-tab-tx').click();
  expect((await history).status()).toBe(200);
  const rows = page.getByTestId('tx-row');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0).getByTestId('tx-row-type')).toHaveText('출금');
  await expect(rows.nth(0).getByTestId('tx-row-amount')).toHaveText(`${MINUS}30,000`);
  await expect(rows.nth(0).getByTestId('tx-row-balance-after')).toHaveText('70,000');
  await expect(rows.nth(1).getByTestId('tx-row-type')).toHaveText('입금');
  await expect(rows.nth(1).getByTestId('tx-row-amount')).toHaveText('+100,000');
  await expect(rows.nth(1).getByTestId('tx-row-balance-after')).toHaveText('100,000');
  await expect(page.getByTestId('account-balance')).toHaveText('70,000');
  // 줄 사이 잔액 연속성(한 페이지 안): 100,000 − 30,000 = 70,000, 첫 거래는 0원에서 시작
  await expect(rows.nth(0).getByTestId('tx-row-continuity')).toHaveAttribute('data-state', 'linked');
  await expect(rows.nth(1).getByTestId('tx-row-continuity')).toHaveAttribute('data-state', 'linked');
  await shot(page, 'e02-account-tx-history');
});
