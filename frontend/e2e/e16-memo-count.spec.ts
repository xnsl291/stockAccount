// E-16 거래 메모 글자 수 표시. 255자까지는 회색, 넘으면 빨간색이고 다음 단계로 못 넘어감

import { expect, test } from '@playwright/test';
import { apiDeposit, closeRedis, createMember } from './support/backend';
import { loginUI, shot } from './support/ui';

test.afterAll(closeRedis);

test('E-16 입금·출금·송금 메모: 글자 수가 바뀌고 256자에서 막히고 255자에서 풀림', async ({ page }) => {
  const a = await createMember();
  const b = await createMember();
  await apiDeposit(a, 100_000);
  await loginUI(page, a);

  // 송금은 받는 사람 확인까지 해야 다음 단계가 열림
  await page.goto(`/transfer?accountId=${a.accountId}`);
  await page.getByTestId('transfer-to-number-input').fill(b.accountNumber);
  await page.getByTestId('transfer-to-check').click();
  await expect(page.getByTestId('transfer-to-name')).toHaveText(b.name);

  const pages = [
    { p: 'transfer', url: null },
    { p: 'deposit', url: `/deposit?accountId=${a.accountId}` },
    { p: 'withdraw', url: `/withdraw?accountId=${a.accountId}` },
  ];
  for (const { p, url } of pages) {
    if (url) await page.goto(url);
    const memo = page.getByTestId(`${p}-memo-input`);
    const count = page.getByTestId(`${p}-memo-count`);
    const next = page.getByTestId(`${p}-next`);
    await page.getByTestId(`${p}-amount-input`).fill('1000');

    await expect(count).toHaveText('0/255');
    await memo.fill('생활비 😀');
    await expect(count).toHaveText('5/255');
    await expect(next).toBeEnabled();

    await memo.fill('가'.repeat(255));
    await expect(count).toHaveText('255/255');
    await expect(next).toBeEnabled();
    if (p === 'deposit') await shot(page, 'e16-memo-normal');

    await memo.fill('가'.repeat(256));
    await expect(count).toHaveText('256/255');
    await expect(count).toHaveCSS('color', 'rgb(196, 38, 29)');
    await expect(memo).toHaveAttribute('aria-invalid', 'true');
    await expect(next).toBeDisabled();
    if (p === 'deposit') await shot(page, 'e16-memo-over');

    await memo.fill('가'.repeat(255));
    await expect(memo).not.toHaveAttribute('aria-invalid', 'true');
    await expect(next).toBeEnabled();
  }
});
