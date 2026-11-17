// E-01 가입과 첫 로그인(설계 3.13)

import { expect, test } from '@playwright/test';
import { clearSmsIpLimit, closeRedis, newName, newPhone, readSmsCode } from './support/backend';
import { shot, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-01 문자 인증으로 가입하고 첫 로그인하면 계좌 1개, 잔액 0원', async ({ page }) => {
  const phone = newPhone();
  const name = newName();

  // 1 전화번호 → 인증번호 받기
  await page.goto('/signup');
  await page.getByTestId('signup-phone-input').fill(phone);
  await clearSmsIpLimit();
  const sent = waitApi(page, 'POST', '/api/auth/send-code');
  await page.getByTestId('signup-send-code').click();
  expect((await sent).status()).toBe(200);
  await expect(page.getByTestId('signup-code-timer')).toBeVisible();

  // 2 Redis에서 읽은 인증번호로 확인
  await page.getByTestId('signup-code-input').fill(await readSmsCode(phone));
  const verified = waitApi(page, 'POST', '/api/auth/verify-phone');
  await page.getByTestId('signup-verify').click();
  expect((await verified).status()).toBe(200);
  await expect(page.getByTestId('signup-verified-notice')).toBeVisible();
  await expect(page.getByTestId('signup-name-input')).toBeVisible();

  // 3 정보 입력 → 가입 → 로그인 화면
  await page.getByTestId('signup-name-input').fill(name);
  await page.getByTestId('signup-login-pw-input').fill('login-pw-1');
  await page.getByTestId('signup-login-pw-confirm-input').fill('login-pw-1');
  await page.getByTestId('signup-account-pw-input').fill('acct-pw-1');
  await page.getByTestId('signup-account-pw-confirm-input').fill('acct-pw-1');
  const signedUp = waitApi(page, 'POST', '/api/auth/sign-up');
  await page.getByTestId('signup-submit').click();
  expect((await signedUp).status()).toBe(200);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-notice')).toHaveText(/^가입이 끝났어요/);

  // 4 로그인 → 홈
  await page.getByTestId('login-phone-input').fill(phone);
  await page.getByTestId('login-password-input').fill('login-pw-1');
  const loggedIn = waitApi(page, 'POST', '/api/auth/login');
  const accounts = waitApi(page, 'GET', '/api/accounts');
  await page.getByTestId('login-submit').click();
  expect((await loggedIn).status()).toBe(200);
  expect((await accounts).status()).toBe(200);
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByTestId('home-account-card')).toHaveCount(1);
  await expect(page.getByTestId('home-account-card-balance')).toHaveText('0');
  await expect(page.getByTestId('home-total-balance')).toHaveText('0');
  await expect(page.getByTestId('app-user-name')).toHaveText(name);
  await shot(page, 'e01-home-after-signup');
});
