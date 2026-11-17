// E-08 토큰 재발급과 로그아웃. 액세스 토큰 5초짜리 앱(short-token 프로젝트)에서 돌림(설계 3.4 E-08)

import { expect, test } from '@playwright/test';
import { E2E } from './config.mjs';
import { closeRedis, createMember } from './support/backend';
import { loginUI, waitApi } from './support/ui';

test.afterAll(closeRedis);

test('E-08 만료 뒤에도 로그인 화면 없이 재발급 1번으로 목록이 뜨고, 로그아웃 뒤에는 어디로 가도 로그인 화면', async ({ page }) => {
  const m = await createMember();

  // 1 로그인
  await loginUI(page, m);

  // 2 만료를 기다린 뒤 계좌 목록
  await page.waitForTimeout((E2E.shortAccessSeconds + 1.5) * 1000);
  const reissues: number[] = [];
  page.on('response', (r) => {
    if (new URL(r.url()).pathname === '/api/auth/reissue') reissues.push(r.status());
  });
  const expiredTry = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/accounts' && r.status() === 401);
  const retried = page.waitForResponse((r) => new URL(r.url()).pathname === '/api/accounts' && r.status() === 200);
  await page.getByTestId('app-nav-accounts').click();
  expect(((await (await expiredTry).json()) as { code: string }).code).toBe('EXPIRED_TOKEN');
  await retried;
  await expect(page.getByTestId('accounts-row')).toHaveCount(1);
  await expect(page).not.toHaveURL(/\/login/);
  expect(reissues).toEqual([200]);

  // 3 로그아웃
  const loggedOut = waitApi(page, 'POST', '/api/auth/logout');
  await page.getByTestId('app-logout').click();
  expect((await loggedOut).status()).toBe(200);
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-notice')).toHaveText('로그아웃했어요');

  // 4 뒤로 가기, 주소 직접 입력
  await page.goBack();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-phone-input')).toBeVisible();
  await page.goto('/accounts');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-phone-input')).toBeVisible();
});
