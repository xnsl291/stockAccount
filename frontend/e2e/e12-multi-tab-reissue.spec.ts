// E-12 여러 탭에서 재발급이 겹침(제안 시나리오). 액세스 토큰 5초짜리 앱에서 돌림
// 탭 사이 규칙: Web Locks로 잠근 뒤 저장소를 다시 읽어, 다른 탭이 이미 재발급했으면 그 토큰을 씀(설계 3.5.3)

import { expect, test } from '@playwright/test';
import { E2E } from './config.mjs';
import { closeRedis, createMember } from './support/backend';
import { loginUI } from './support/ui';

test.afterAll(closeRedis);

test('E-12 만료 뒤 두 탭을 동시에 새로 고쳐도 재발급은 한 번, 두 탭 모두 로그인 유지', async ({ page, context }) => {
  const m = await createMember();
  await loginUI(page, m);
  const tab2 = await context.newPage();
  await page.goto('/accounts');
  await tab2.goto('/accounts');
  await expect(page.getByTestId('accounts-row')).toHaveCount(1);
  await expect(tab2.getByTestId('accounts-row')).toHaveCount(1);

  await page.waitForTimeout((E2E.shortAccessSeconds + 1.5) * 1000);
  const reissues: number[] = [];
  context.on('response', (r) => {
    if (new URL(r.url()).pathname === '/api/auth/reissue') reissues.push(r.status());
  });

  await Promise.all([page.reload(), tab2.reload()]);

  for (const p of [page, tab2]) {
    await expect(p.getByTestId('accounts-row')).toHaveCount(1);
    await expect(p).not.toHaveURL(/\/login/);
  }
  expect(reissues).toEqual([200]);
  const refresh = await page.evaluate(() => localStorage.getItem('stockaccount.refreshToken'));
  expect(refresh).toBeTruthy();
});
