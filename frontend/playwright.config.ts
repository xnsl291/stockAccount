import { defineConfig, devices } from '@playwright/test';
import { E2E } from './e2e/config.mjs';

// 백엔드 두 개(기본·짧은 만료)와 화면 미리보기 서버 두 개를 띄우고 돌림. 준비(컨테이너·빌드)는 e2e/prepare.mjs
// 1차는 작업자 1개(직렬). 시나리오마다 새 회원·번호·종목을 써서 병렬로 바꿔도 서로 깨지지 않게 짬(설계 3.4.2)
const shortSpecs = /e(08|12)-.*\.spec\.ts/;

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  globalSetup: './e2e/global-setup.ts',
  use: {
    ...devices['Desktop Chrome'],
    locale: 'ko-KR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'main', testIgnore: shortSpecs, use: { baseURL: `http://localhost:${E2E.webPort}` } },
    { name: 'short-token', testMatch: shortSpecs, use: { baseURL: `http://localhost:${E2E.shortWebPort}` } },
  ],
  webServer: [
    {
      command: 'node e2e/start-backends.mjs',
      url: `http://localhost:${E2E.shortBackendPort}/api/accounts`,
      timeout: 240_000,
      reuseExistingServer: true,
      stdout: 'pipe',
    },
    {
      command: `npx vite preview --port ${E2E.webPort} --strictPort`,
      url: `http://localhost:${E2E.webPort}`,
      env: { API_TARGET: `http://localhost:${E2E.backendPort}` },
      reuseExistingServer: true,
    },
    {
      command: `npx vite preview --port ${E2E.shortWebPort} --strictPort`,
      url: `http://localhost:${E2E.shortWebPort}`,
      env: { API_TARGET: `http://localhost:${E2E.shortBackendPort}` },
      reuseExistingServer: true,
    },
  ],
});
