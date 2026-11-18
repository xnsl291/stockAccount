// E2E용 백엔드 두 개를 차례로 띄움(Playwright webServer가 부름)
//  - 8081: 기본 설정(액세스 토큰 30분)
//  - 8082: 액세스 토큰 5초(E-08 토큰 재발급, E-12 여러 탭 재발급). 만료 설정은 앱 전체에 걸려 따로 띄움(설계 3.4 E-08)
// 둘 다 빈 DB에 테이블을 만들기 때문에 동시에 띄우면 겹칠 수 있어 앞의 것이 뜬 뒤 다음 것을 띄움
// 로그는 e2e/.logs/ 아래. 문자는 보내지 않고 로그만 남김(sms.provider=log)

import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { E2E } from './config.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = process.env.BACKEND_DIR ? path.resolve(process.env.BACKEND_DIR) : path.resolve(here, '..', '..'); // prepare.mjs와 같은 규칙
const libs = path.join(repo, 'build', 'libs');
const jar = fs
  .readdirSync(libs)
  .filter((f) => f.endsWith('.jar') && !f.endsWith('-plain.jar'))
  .map((f) => path.join(libs, f))[0];
if (!jar) {
  console.error('백엔드 jar가 없음. npm run e2e(준비 스크립트)를 먼저 돌려 주세요');
  process.exit(1);
}
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const logDir = path.join(here, '.logs');
fs.mkdirSync(logDir, { recursive: true });

// 테스트 실행마다 새로 만든 서명 키. 두 앱이 같이 씀
const secret = randomBytes(48).toString('base64');
const children = [];

function start(port, accessSeconds) {
  const log = fs.openSync(path.join(logDir, `backend-${port}.log`), 'w');
  const child = spawn(java, ['-jar', jar, `--server.port=${port}`], {
    env: {
      ...process.env,
      DB_URL: `jdbc:mysql://localhost:${E2E.mysqlPort}/accountSystem?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`,
      DB_USERNAME: 'account',
      DB_PASSWORD: 'account',
      REDIS_HOST: 'localhost',
      REDIS_PORT: String(E2E.redisPort),
      JWT_SECRET: secret,
      JWT_ACCESS_TOKEN_SECONDS: String(accessSeconds),
      SMS_PROVIDER: 'log',
      // 백엔드의 지표 포트 기본값(8081)이 E2E 업무 포트와 겹쳐서 앱마다 따로 줌
      MANAGEMENT_PORT: String(port + 100),
    },
    stdio: ['ignore', log, log],
  });
  child.on('exit', (code) => {
    console.error(`백엔드 ${port} 종료(code ${code}). 로그: e2e/.logs/backend-${port}.log`);
    process.exit(1);
  });
  children.push(child);
}

async function waitUp(port) {
  for (let i = 0; i < 180; i++) {
    try {
      const res = await fetch(`http://localhost:${port}/api/accounts`);
      if (res.status === 401) return; // 떠 있고 인증 필터까지 돎
    } catch {
      // 아직 안 뜸
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`백엔드 ${port}이 3분 안에 뜨지 않음`);
}

function stopAll() {
  for (const c of children) {
    c.removeAllListeners('exit');
    c.kill();
  }
}
process.on('SIGINT', () => (stopAll(), process.exit(0)));
process.on('SIGTERM', () => (stopAll(), process.exit(0)));
process.on('exit', stopAll);

start(E2E.backendPort, 1800);
await waitUp(E2E.backendPort);
console.log(`백엔드 ${E2E.backendPort} 준비됨`);
start(E2E.shortBackendPort, E2E.shortAccessSeconds);
await waitUp(E2E.shortBackendPort);
console.log(`백엔드 ${E2E.shortBackendPort} 준비됨(액세스 토큰 ${E2E.shortAccessSeconds}초)`);
setInterval(() => {}, 1 << 30); // Playwright가 끝낼 때까지 살아 있음
