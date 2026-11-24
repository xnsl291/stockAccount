// 화면 조작 도우미와 확인용 스크린샷

import path from 'node:path';
import { expect, type Page, type Response } from '@playwright/test';
import type { TestMember } from './backend';

export const MINUS = '−';

/** 로그인 화면에서 로그인하고 첫 화면을 기다림 */
export async function loginUI(page: Page, member: Pick<TestMember, 'phone' | 'password'>, expectPath: RegExp = /\/$/): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login-phone-input').fill(member.phone);
  await page.getByTestId('login-password-input').fill(member.password);
  const res = waitApi(page, 'POST', '/api/auth/login');
  await page.getByTestId('login-submit').click();
  expect((await res).status()).toBe(200);
  await expect(page).toHaveURL(expectPath);
}

/** 경로 끝이 맞는 API 응답을 기다림. path는 쿼리 앞까지 비교함 */
export function waitApi(page: Page, method: string, pathPattern: string | RegExp): Promise<Response> {
  return page.waitForResponse((r) => {
    if (r.request().method() !== method) return false;
    const p = new URL(r.url()).pathname;
    return typeof pathPattern === 'string' ? p === pathPattern : pathPattern.test(p);
  });
}

/** 입력 단계 → 확인 → 비밀번호 칸까지. 확인 영역이 열린 것을 확인함 */
export async function toPasswordStep(page: Page, p: string): Promise<void> {
  await page.getByTestId(`${p}-next`).click();
  await expect(page.getByTestId(`${p}-confirm-panel`)).toBeVisible();
  await page.getByTestId(`${p}-confirm-next`).click();
  await expect(page.getByTestId(`${p}-pw-panel`)).toBeVisible();
}

/**
 * 통과한 화면 단계 스크린샷. E2E_SCREENSHOT_DIR이 있으면 그곳에, 없으면 test-results/screenshots에 남김
 */
export async function shot(page: Page, name: string): Promise<void> {
  const dir = process.env.E2E_SCREENSHOT_DIR ?? path.join('test-results', 'screenshots');
  await page.screenshot({ path: path.join(dir, `${name}.png`), fullPage: true });
}
