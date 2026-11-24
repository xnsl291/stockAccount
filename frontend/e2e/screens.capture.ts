// 화면 캡처(검증용, 기본 E2E에는 들어가지 않음). 실제 백엔드에 회원·종목·주문을 만든 뒤
// 데스크톱 1440, 태블릿 768, 휴대폰 390 너비로 주요 화면을 찍고, 가로 넘침(문서 폭 > 창 폭)을 같이 기록함
// 실행: CAPTURE_DIR=<저장 폴더> npx playwright test -c playwright.capture.config.ts

import fs from 'node:fs';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { ADMIN_FILE, apiDeposit, closeRedis, createMember, registerStock, type TestMember } from './support/backend';
import { loginUI, toPasswordStep } from './support/ui';

const DIR = process.env.CAPTURE_DIR ?? path.join('test-results', 'capture');
const WIDTHS = [
  { name: 'desktop-1440', width: 1440, height: 900 },
  { name: 'tablet-768', width: 768, height: 1024 },
  { name: 'mobile-390', width: 390, height: 844 },
];
const overflow: Record<string, number> = {};

test.afterAll(async () => {
  fs.writeFileSync(path.join(DIR, 'overflow.json'), JSON.stringify(overflow, null, 2));
  await closeRedis();
});

async function snap(page: Page, size: string, name: string) {
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
  const extra = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  overflow[`${size}/${name}`] = extra;
  await page.screenshot({ path: path.join(DIR, size, `${name}.png`), fullPage: true });
}

test.setTimeout(600_000);

test('주요 화면 캡처', async ({ browser }) => {
  fs.mkdirSync(DIR, { recursive: true });
  const m: TestMember = await createMember({ name: '이서준' });
  await apiDeposit(m, 5_000_000);
  const stock = await registerStock(70000, '한빛전자');

  // 준비: 미체결 매수 1건(65,000원 × 10주), 바로 체결되는 매수 1건(70,000원 × 5주), 출금 1건
  // 관리자 시세 입력 결과를 너비마다 찍으려고 미체결 매수 3건(63,000·62,000·61,000원 × 1주)을 더 넣음
  const setup = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await loginUI(setup, m);
  for (const [price, qty] of [
    ['65000', '10'],
    ['70000', '5'],
    ['63000', '1'],
    ['62000', '1'],
    ['61000', '1'],
  ]) {
    await setup.goto(`/order?stockId=${stock.id}&side=buy&accountId=${m.accountId}`);
    await setup.getByTestId('order-price-input').fill(price);
    await setup.getByTestId('order-quantity-input').fill(qty);
    await toPasswordStep(setup, 'order');
    await setup.getByTestId('order-pw-input').fill(m.accountPassword);
    await setup.getByTestId('order-submit').click();
    await expect(setup.getByTestId('order-result-panel')).toBeVisible();
  }
  await setup.goto(`/withdraw?accountId=${m.accountId}`);
  await setup.getByTestId('withdraw-amount-input').fill('120000');
  await setup.getByTestId('withdraw-memo-input').fill('생활비');
  await toPasswordStep(setup, 'withdraw');
  await setup.getByTestId('withdraw-pw-input').fill(m.accountPassword);
  await setup.getByTestId('withdraw-submit').click();
  await expect(setup.getByTestId('withdraw-result-panel')).toBeVisible();
  await snap(setup, 'desktop-1440', 'withdraw-result');
  const storage = await setup.context().storageState();
  await setup.close();

  const admin = JSON.parse(fs.readFileSync(ADMIN_FILE, 'utf-8')) as { phone: string; password: string };

  const adminPrices = ['63000', '62000', '61000'];
  for (const [i, size] of WIDTHS.entries()) {
    fs.mkdirSync(path.join(DIR, size.name), { recursive: true });
    const anon = await browser.newPage({ viewport: size });
    await anon.goto('/login');
    await snap(anon, size.name, 'login');
    await anon.goto('/reset-password');
    await snap(anon, size.name, 'reset-password');
    await anon.goto('/signup');
    await snap(anon, size.name, 'signup');
    await anon.close();

    const ctx = await browser.newContext({ viewport: size, storageState: storage, locale: 'ko-KR' });
    const page = await ctx.newPage();
    await page.goto('/');
    await expect(page.getByTestId('home-total-balance')).toBeVisible();
    await snap(page, size.name, 'home');
    await page.getByTestId('app-display-menu').click();
    await snap(page, size.name, 'home-menu-open');
    await page.getByTestId('app-text-size-toggle').click();
    await page.keyboard.press('Escape');
    await snap(page, size.name, 'home-large-text');
    await page.getByTestId('app-display-menu').click();
    await page.getByTestId('app-text-size-toggle').click();
    await page.getByTestId('app-privacy-toggle').click();
    await page.keyboard.press('Escape');
    await snap(page, size.name, 'home-privacy');
    await page.getByTestId('app-display-menu').click();
    await page.getByTestId('app-privacy-toggle').click();
    await page.keyboard.press('Escape');

    await page.getByTestId('support-launch').click();
    await page.getByRole('button', { name: '출금 가능 금액' }).click();
    await page.getByTestId('support-agent').click();
    await page.screenshot({ path: path.join(DIR, size.name, 'support-chat.png') });
    await page.getByTestId('support-close').click();

    await page.goto('/accounts');
    await snap(page, size.name, 'accounts');
    await page.goto(`/accounts/${m.accountId}`);
    await expect(page.getByTestId('tx-row').first()).toBeVisible();
    await snap(page, size.name, 'account-tx');
    await page.getByTestId('account-tab-orders').click();
    await expect(page.getByTestId('orders-row').first()).toBeVisible();
    await page.getByTestId('orders-row-cancel').first().click();
    await snap(page, size.name, 'account-orders-cancel');
    await page.getByTestId('account-tab-holdings').click();
    await expect(page.getByTestId('holdings-row').first()).toBeVisible();
    await snap(page, size.name, 'account-holdings');
    await page.goto('/stocks');
    await page.getByTestId('search-keyword-input').fill(stock.code);
    await page.getByTestId('search-submit').click();
    await expect(page.getByTestId('search-row')).toHaveCount(1);
    await snap(page, size.name, 'search');
    await page.goto(`/stocks/${stock.id}`);
    await snap(page, size.name, 'stock');
    await page.goto(`/order?stockId=${stock.id}&side=buy&accountId=${m.accountId}`);
    await page.getByTestId('order-price-input').fill('68000');
    await page.getByTestId('order-quantity-input').fill('3');
    await snap(page, size.name, 'order-input');
    await page.goto(`/withdraw?accountId=${m.accountId}`);
    await page.getByTestId('withdraw-amount-input').fill('9999999');
    await snap(page, size.name, 'withdraw-error');
    await page.getByTestId('withdraw-amount-input').fill('10000');
    await toPasswordStep(page, 'withdraw');
    await snap(page, size.name, 'withdraw-password');
    await page.goto('/transfer');
    await snap(page, size.name, 'transfer');
    await page.goto('/me');
    await snap(page, size.name, 'me');
    await page.goto(`/accounts/${m.accountId}/settings`);
    await snap(page, size.name, 'account-settings');
    await page.goto('/no-such-page');
    await snap(page, size.name, 'not-found');
    await ctx.close();

    const adminPage = await browser.newPage({ viewport: size });
    await loginUI(adminPage, admin, /\/ops$/);
    await snap(adminPage, size.name, 'ops-home');
    await adminPage.goto('/ops/stocks');
    const stockRow = adminPage.locator(`[data-testid="opsstocks-row"][data-id="${stock.id}"]`);
    await expect(stockRow).toBeVisible();
    await snap(adminPage, size.name, 'ops-stocks');
    await stockRow.getByTestId('opsstocks-row-orders-toggle').click();
    await expect(adminPage.getByTestId('opsstocks-order-row').first()).toBeVisible();
    await snap(adminPage, size.name, 'ops-stocks-orders');
    await adminPage.goto(`/ops/price?stockId=${stock.id}`);
    await adminPage.getByTestId('opsprice-price-input').fill(adminPrices[i]);
    await expect(adminPage.getByTestId('opsprice-order-row-will-fill').first()).toBeVisible();
    await snap(adminPage, size.name, 'ops-price-input');
    await adminPage.getByTestId('opsprice-submit').click();
    await expect(adminPage.getByTestId('opsprice-result-panel')).toBeVisible();
    await snap(adminPage, size.name, 'ops-price-result');
    await adminPage.goto('/ops/recon');
    await snap(adminPage, size.name, 'ops-recon');
    await adminPage.goto('/ops/ai-decision');
    await snap(adminPage, size.name, 'ops-ai-decision');
    await adminPage.close();
  }
});
