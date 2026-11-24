// E-07 계좌 잠금과 해제(설계 3.13)
// 5번째로 틀린 요청의 응답도 UNMATCHED_PASSWORD라, 화면이 계좌를 다시 읽어 거래정지인지 확인함(사용자 결정)

import { expect, test } from '@playwright/test';
import { apiDeposit, clearSmsIpLimit, closeRedis, createMember, readSmsCode } from './support/backend';
import { loginUI, shot, toPasswordStep, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-07 계좌 비밀번호를 5번 틀리면 거래정지, 문자 인증으로 풀고 다시 출금', async ({ page }) => {
  const m = await createMember();
  await apiDeposit(m, 100_000);
  await loginUI(page, m);

  await page.goto(`/withdraw?accountId=${m.accountId}`);
  await page.getByTestId('withdraw-amount-input').fill('10000');
  await toPasswordStep(page, 'withdraw');

  async function wrongOnce() {
    await page.getByTestId('withdraw-pw-input').fill('wrong-password');
    const failed = waitApi(page, 'POST', '/api/transactions/withdrawal');
    const reread = waitApi(page, 'GET', `/api/accounts/${m.accountId}`);
    await page.getByTestId('withdraw-submit').click();
    const res = await failed;
    expect(res.status()).toBe(400);
    expect(((await res.json()) as { code: string }).code).toBe('UNMATCHED_PASSWORD');
    return (await (await reread).json()) as { status: string };
  }

  // 1 네 번 틀림
  for (let i = 0; i < 4; i++) {
    expect((await wrongOnce()).status).toBe('EXISTS');
    await expect(page.getByTestId('withdraw-pw-error')).toHaveText('계좌 비밀번호가 맞지 않아요');
    await expect(page.getByTestId('withdraw-unlock-link')).toBeHidden();
  }

  // 2 다섯 번째 → 거래정지
  expect((await wrongOnce()).status).toBe('PENDING');
  await expect(page.getByTestId('withdraw-pw-error')).toHaveText('비밀번호를 5번 연속 틀려 거래정지됐어요');
  await expect(page.getByTestId('withdraw-unlock-link')).toBeVisible();
  await expect(page.getByTestId('withdraw-submit')).toBeDisabled();
  await shot(page, 'e07-withdraw-locked');

  // 3 계좌 상세
  await page.goto(`/accounts/${m.accountId}`);
  await expect(page.getByTestId('account-status')).toHaveText('거래정지');
  await expect(page.getByTestId('account-status')).toHaveAttribute('data-state', 'PENDING');
  await expect(page.getByTestId('account-locked-banner')).toBeVisible();

  // 4 맞는 비밀번호도 거부됨(화면이 보내기를 잠그므로 API로 확인)
  const token = await page.evaluate(() => localStorage.getItem('stockaccount.accessToken'));
  const direct = await page.request.post('/api/transactions/withdrawal', {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': crypto.randomUUID() },
    data: { accountId: m.accountId, amount: 10000, accountPassword: m.accountPassword },
  });
  expect(direct.status()).toBe(400);
  expect(((await direct.json()) as { code: string }).code).toBe('PENDING_ACCOUNT');

  // 5 잠금 해제 화면
  const memberLoaded = waitApi(page, 'GET', `/api/member/${m.memberId}`);
  await page.getByTestId('account-unlock-link').click();
  expect((await memberLoaded).status()).toBe(200);
  await expect(page.getByTestId('unlock-phone')).toHaveText(`010-****-${m.phone.slice(-4)}`);

  // 6 문자 인증 → 해제
  await clearSmsIpLimit();
  const sent = waitApi(page, 'POST', '/api/auth/send-code');
  await page.getByTestId('unlock-send-code').click();
  expect((await sent).status()).toBe(200);
  await page.getByTestId('unlock-code-input').fill(await readSmsCode(m.phone));
  const verified = waitApi(page, 'POST', '/api/auth/verify-phone');
  const unlocked = waitApi(page, 'POST', `/api/accounts/${m.accountId}/unlock`);
  await page.getByTestId('unlock-submit').click();
  expect((await verified).status()).toBe(200);
  expect((await unlocked).status()).toBe(200);
  await expect(page.getByTestId('unlock-result')).toBeVisible();
  await expect(page.getByTestId('unlock-status')).toHaveText('정상');

  // 7 하던 화면(계좌 상세)으로 돌아가 다시 출금
  await page.getByTestId('unlock-return-link').click();
  await expect(page).toHaveURL(new RegExp(`/accounts/${m.accountId}$`));
  await page.getByTestId('account-withdraw-link').click();
  await page.getByTestId('withdraw-amount-input').fill('10000');
  await toPasswordStep(page, 'withdraw');
  await page.getByTestId('withdraw-pw-input').fill(m.accountPassword);
  const ok = waitApi(page, 'POST', '/api/transactions/withdrawal');
  await page.getByTestId('withdraw-submit').click();
  expect((await ok).status()).toBe(200);
  await expect(page.getByTestId('withdraw-result-balance')).toHaveText('90,000');
});
