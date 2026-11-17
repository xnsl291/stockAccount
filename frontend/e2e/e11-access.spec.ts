// E-11 권한 구분(설계 3.13)

import fs from 'node:fs';
import { expect, test } from '@playwright/test';
import { ADMIN_FILE, closeRedis, createMember, registerStock } from './support/backend';
import { loginUI } from './support/ui';

test.afterAll(closeRedis);

test('E-11 회원은 관리자 주소에서 홈으로, 로그인 전에는 로그인 화면으로, 관리자는 안내 화면으로', async ({ page, browser, baseURL }) => {
  const m = await createMember();
  const stock = await registerStock(10000);

  // 1 회원이 /ops 직접 이동 → 홈
  await loginUI(page, m);
  await page.goto('/ops');
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByTestId('home-total-balance')).toBeVisible();

  // 2 회원 토큰으로 관리자 API → 403
  const token = await page.evaluate(() => localStorage.getItem('stockaccount.accessToken'));
  const res = await page.request.put(`/admin/stocks/${stock.id}/price`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { price: 12000 },
  });
  expect(res.status()).toBe(403);
  expect(((await res.json()) as { code: string }).code).toBe('ACCESS_DENIED');

  // 3 로그인하지 않은 새 컨텍스트 → 로그인 화면
  const anon = await browser.newContext({ baseURL });
  const anonPage = await anon.newPage();
  await anonPage.goto('/accounts');
  await expect(anonPage).toHaveURL(/\/login$/);

  // 4 관리자 로그인 → /ops 안내
  const admin = JSON.parse(fs.readFileSync(ADMIN_FILE, 'utf-8')) as { phone: string; password: string };
  await loginUI(anonPage, admin, /\/ops$/);
  await expect(anonPage.getByTestId('adminwait-panel')).toBeVisible();
  // 관리자가 회원 주소로 가면 관리자 첫 화면으로
  await anonPage.goto('/accounts');
  await expect(anonPage).toHaveURL(/\/ops$/);
  await anon.close();
});
