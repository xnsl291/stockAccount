// 화면 캡처 전용 설정. 서버 띄우는 방법은 기본 설정(playwright.config.ts)을 그대로 쓰고, 캡처 파일만 돌림
import base from './playwright.config';
import { defineConfig } from '@playwright/test';
import { E2E } from './e2e/config.mjs';

export default defineConfig({
  ...base,
  testMatch: /screens\.capture\.ts/,
  projects: [{ name: 'capture', use: { ...base.use, baseURL: `http://localhost:${E2E.webPort}` } }],
});
