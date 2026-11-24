/// <reference types="vitest/config" />
import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

// 개발·미리보기 서버가 /api, /admin 요청을 백엔드로 넘김(프록시). 브라우저 입장에서는 같은 주소라 백엔드에 CORS 설정이 없어도 됨
// E2E는 API_TARGET으로 E2E 전용 백엔드를 가리킴
const apiTarget = process.env.API_TARGET ?? 'http://localhost:8080';
const proxy = {
  '/api': { target: apiTarget },
  '/admin': { target: apiTarget },
};

// 토큰을 localStorage에 두므로 우리 주소의 스크립트만 돌게 함(XSS 피해 줄이기).
// 개발 서버는 React 새로 고침 코드를 HTML 안에 직접 넣어 이 정책과 맞지 않아, 빌드 결과에만 넣음. E2E는 빌드 결과로 돌림
const CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
  "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'";

function cspMeta(): Plugin {
  return {
    name: 'csp-meta',
    apply: 'build',
    transformIndexHtml: () => [
      { tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: CSP }, injectTo: 'head-prepend' },
    ],
  };
}

export default defineConfig({
  plugins: [react(), cspMeta()],
  server: { proxy },
  preview: { proxy },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
  },
});
