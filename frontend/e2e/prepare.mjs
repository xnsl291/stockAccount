// E2E 전에 한 번 돌림: ① E2E 전용 MySQL·Redis 띄우기 ② 백엔드 실행 파일(jar) 만들기 ③ 화면 빌드
// 화면은 개발 서버가 아니라 빌드 결과(vite preview)로 돌려 CSP까지 같이 확인함

import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const frontend = path.resolve(here, '..');
const repo = path.resolve(frontend, '..');

function run(cmd, opts = {}) {
  console.log(`> ${cmd}`);
  execSync(cmd, { stdio: 'inherit', ...opts });
}

run(`docker compose -f "${path.join(here, 'docker-compose.e2e.yml')}" up -d --wait`);

if (!process.env.SKIP_BACKEND_BUILD) {
  if (!process.env.JAVA_HOME) {
    console.error('JAVA_HOME이 없음. JDK 17 경로를 넣어 주세요 (예: export JAVA_HOME=/c/project/tools/jdk/jdk-17.0.21+9)');
    process.exit(1);
  }
  const gradlew = path.join(repo, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  run(`"${gradlew}" bootJar -q`, { cwd: repo });
}

run('npx vite build', { cwd: frontend });
