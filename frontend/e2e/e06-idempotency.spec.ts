// E-06 중복 요청 방지(설계 3.13)
// ① 서버는 처리했는데 브라우저만 응답을 못 받는 상황: 첫 요청을 서버로 실제로 보낸 뒤(route.fetch) 브라우저에는 네트워크 실패를 돌려줌
// ② 보내기를 빠르게 두 번 누름

import { expect, test } from '@playwright/test';
import { apiDeposit, closeRedis, createMember, getAccount } from './support/backend';
import { MINUS, loginUI, toPasswordStep, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-06 응답을 잃고 다시 시도해도, 두 번 눌러도 출금은 한 번만 됨', async ({ page }) => {
  const m = await createMember();
  await apiDeposit(m, 100_000);
  await loginUI(page, m);

  const sentKeys: string[] = [];
  let dropNext = true;
  await page.route('**/api/transactions/withdrawal', async (route) => {
    sentKeys.push(route.request().headers()['idempotency-key']);
    if (dropNext) {
      dropNext = false;
      const serverResponse = await route.fetch(); // 서버는 실제로 처리함
      expect(serverResponse.status()).toBe(200);
      await route.abort('failed'); // 브라우저는 응답을 못 받음
      return;
    }
    await route.continue();
  });

  // 1 첫 출금: 서버는 200, 브라우저는 실패
  await page.goto(`/withdraw?accountId=${m.accountId}`);
  await page.getByTestId('withdraw-amount-input').fill('30000');
  await toPasswordStep(page, 'withdraw');
  await page.getByTestId('withdraw-pw-input').fill(m.accountPassword);
  await page.getByTestId('withdraw-submit').click();
  await expect(page.getByTestId('withdraw-send-error')).toHaveText('처리됐는지 확인하지 못했어요. 다시 시도해도 두 번 처리되지 않아요');
  await expect(page.getByTestId('withdraw-retry')).toBeVisible();
  expect((await getAccount(m)).balance).toBe(70_000); // 서버에서는 이미 처리됨

  // 2 다시 시도: 같은 키 → 서버가 처음 결과를 돌려줌
  const retried = waitApi(page, 'POST', '/api/transactions/withdrawal');
  await page.getByTestId('withdraw-retry').click();
  expect((await retried).status()).toBe(200);
  await expect(page.getByTestId('withdraw-result-balance')).toHaveText('70,000');
  expect(sentKeys).toHaveLength(2);
  expect(sentKeys[0]).toBeTruthy();
  expect(sentKeys[1]).toBe(sentKeys[0]);

  // 3 거래내역에 출금은 한 줄뿐
  await page.goto(`/accounts/${m.accountId}`);
  const history = waitApi(page, 'GET', `/api/transactions/${m.accountId}/history`);
  await page.getByTestId('account-tab-tx').click();
  expect((await history).status()).toBe(200);
  await expect(page.getByTestId('tx-row')).toHaveCount(2); // 입금(준비) + 출금
  await expect(page.getByTestId('tx-row-type').filter({ hasText: '출금' })).toHaveCount(1);
  await expect(page.getByTestId('tx-row').nth(0).getByTestId('tx-row-amount')).toHaveText(`${MINUS}30,000`);

  // 4 새 출금 10,000을 빠르게 두 번 누름
  sentKeys.length = 0;
  await page.goto(`/withdraw?accountId=${m.accountId}`);
  await page.getByTestId('withdraw-amount-input').fill('10000');
  await toPasswordStep(page, 'withdraw');
  await page.getByTestId('withdraw-pw-input').fill(m.accountPassword);
  const done = waitApi(page, 'POST', '/api/transactions/withdrawal');
  await page.getByTestId('withdraw-submit').dblclick();
  expect((await done).status()).toBe(200);
  await expect(page.getByTestId('withdraw-result-balance')).toHaveText('60,000');
  expect(sentKeys.length).toBeGreaterThanOrEqual(1);
  expect(new Set(sentKeys).size).toBe(1); // 요청이 둘이어도 키는 같음
  expect((await getAccount(m)).balance).toBe(60_000);
});
