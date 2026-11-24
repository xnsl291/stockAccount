// E-09 송금(설계 3.13). 같은 계좌번호는 C7 응답의 계좌 ID로 화면이 미리 막음

import { expect, test } from '@playwright/test';
import { apiDeposit, closeRedis, createMember } from './support/backend';
import { loginUI, shot, toPasswordStep, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-09 A가 B에게 50,000원 송금, 같은 계좌로는 못 보냄', async ({ page, browser, baseURL }) => {
  const a = await createMember();
  const b = await createMember();
  await apiDeposit(a, 100_000);
  await loginUI(page, a);

  // 1 받는 사람 확인
  await page.goto(`/transfer?accountId=${a.accountId}`);
  await page.getByTestId('transfer-from-select').selectOption(String(a.accountId));
  await page.getByTestId('transfer-to-number-input').fill(b.accountNumber);
  const searched = waitApi(page, 'GET', `/api/accounts/search/${b.accountNumber}`);
  await page.getByTestId('transfer-to-check').click();
  expect((await searched).status()).toBe(200);
  await expect(page.getByTestId('transfer-to-name')).toHaveText(b.name);

  // 2 금액 → 확인
  await page.getByTestId('transfer-amount-input').fill('50000');
  await page.getByTestId('transfer-next').click();
  await expect(page.getByTestId('transfer-confirm-to-name')).toHaveText(b.name);
  await expect(page.getByTestId('transfer-confirm-amount')).toHaveText('50,000');
  await shot(page, 'e09-transfer-confirm');

  // 3 비밀번호 → 송금
  await page.getByTestId('transfer-confirm-next').click();
  await page.getByTestId('transfer-pw-input').fill(a.accountPassword);
  const sent = waitApi(page, 'POST', `/api/transactions/${a.accountId}/transfer/${b.accountId}`);
  const reread = waitApi(page, 'GET', `/api/accounts/${a.accountId}`);
  await page.getByTestId('transfer-submit').click();
  expect((await sent).status()).toBe(200);
  expect((await reread).status()).toBe(200);
  await expect(page.getByTestId('transfer-result-balance')).toHaveText('50,000');
  await expect(page.getByTestId('transfer-result-message')).toHaveText(`${b.name}님에게 보냈어요`);

  // 4 B로 로그인한 다른 브라우저 컨텍스트
  const bContext = await browser.newContext({ baseURL });
  const bPage = await bContext.newPage();
  await loginUI(bPage, b);
  await bPage.goto(`/accounts/${b.accountId}`);
  await expect(bPage.getByTestId('account-balance')).toHaveText('50,000');
  const first = bPage.getByTestId('tx-row').nth(0);
  await expect(first.getByTestId('tx-row-type')).toHaveText('받은 송금');
  await expect(first.getByTestId('tx-row-amount')).toHaveText('+50,000');
  await bContext.close();

  // 5 A 자기 계좌번호 → 화면이 막음
  await page.goto(`/transfer?accountId=${a.accountId}`);
  await page.getByTestId('transfer-to-number-input').fill(a.accountNumber);
  const self = waitApi(page, 'GET', `/api/accounts/search/${a.accountNumber}`);
  await page.getByTestId('transfer-to-check').click();
  expect((await self).status()).toBe(200);
  await expect(page.getByTestId('transfer-to-error')).toHaveText('같은 계좌로는 보낼 수 없어요');
  await page.getByTestId('transfer-amount-input').fill('1000');
  await expect(page.getByTestId('transfer-next')).toBeDisabled();
});

test('E-09 보조: 비밀번호 단계까지 간 송금도 비밀번호 칸이 비면 보낼 수 없음', async ({ page }) => {
  const a = await createMember();
  const b = await createMember();
  await apiDeposit(a, 10_000);
  await loginUI(page, a);
  await page.goto(`/transfer?accountId=${a.accountId}`);
  await page.getByTestId('transfer-to-number-input').fill(b.accountNumber);
  await page.getByTestId('transfer-to-check').click();
  await expect(page.getByTestId('transfer-to-name')).toHaveText(b.name);
  await page.getByTestId('transfer-amount-input').fill('1000');
  await toPasswordStep(page, 'transfer');
  await expect(page.getByTestId('transfer-submit')).toBeDisabled();
  await expect(page.getByTestId('transfer-pw-warning')).toHaveText('5번 연속 틀리면 계좌가 거래정지됨');
});
