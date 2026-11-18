# StockAccount 화면 (frontend)

React + Vite + TypeScript. 화면 정의(요소·data-testid·문구)는 설계 문서 `설계_3_화면.md` 3.10~3.13을 따르고, 디자인 반영 결과는 3.15에 있음.

- 스타일: `src/styles/tokens.css`(색·글자 단계·간격, 큰 글씨 모드) → `base.css`(버튼·입력·면) → `layout.css`(메뉴·윗줄·상담) → `pages.css`(머리 카드·행 목록·막대). 입구는 `app.css`
- 글꼴: Pretendard(SIL OFL 1.1)를 npm 패키지에서 받아 빌드에 같이 넣음. 외부 CDN 없음
- 상담하기는 시연용 자동응답. 서버 경계는 `src/api/assistant.ts` 한 곳

## 개발 서버

백엔드(8080)를 먼저 띄운 뒤 실행함. `/api`, `/admin` 요청은 Vite 프록시가 백엔드로 넘기므로 백엔드에 CORS 설정이 없어도 됨.

```bash
npm install
npm run dev          # http://localhost:5173
# 다른 백엔드 주소: API_TARGET=http://localhost:8081 npm run dev
```

## 단위 테스트

```bash
npm test             # vitest: 토큰 재발급 묶기, 중복 요청 방지 키, 금액·수수료 계산
```

## E2E (Playwright)

Docker와 JDK 17이 필요함. 아래 명령 하나가 E2E 전용 MySQL(3317)·Redis(6389) 컨테이너를 띄우고, 백엔드 jar와 화면을 빌드한 뒤, 백엔드 두 개(8081 기본, 8082 액세스 토큰 5초)와 미리보기 서버 두 개(5174, 5175)를 띄워 돌림.

```bash
export JAVA_HOME=/c/project/tools/jdk/jdk-17.0.21+9   # JDK 17 경로
npx playwright install chromium                         # 처음 한 번
npm run e2e
npm run e2e:down                                        # 컨테이너 정리(데이터는 메모리라 함께 사라짐)
```

- 인증번호는 E2E 전용 Redis의 `SMS:{숫자만 번호}`에서 읽고, 발송 직전마다 `SMS_SEND_IP:*`를 지움. 개발용 Redis에서는 돌리지 않음
- 백엔드를 고치지 않았으면 `SKIP_BACKEND_BUILD=1 npm run e2e`로 jar 빌드를 건너뜀
- 다른 작업 폴더의 백엔드로 돌리려면 `BACKEND_DIR=<그 폴더> npm run e2e`. jar 빌드와 실행 둘 다 그 폴더를 씀
- 백엔드는 Flyway로 테이블을 만들어서, 다른 방식으로 테이블이 생긴 DB가 남아 있으면 뜨지 않음. 백엔드 브랜치를 바꿨으면 `npm run e2e:down` 뒤에 돌림
- 확인용 스크린샷 위치는 `E2E_SCREENSHOT_DIR`로 정함(없으면 `test-results/screenshots`)
- 백엔드 로그: `e2e/.logs/backend-8081.log`, `backend-8082.log`

## 화면 캡처

E2E와 같은 서버를 띄워 1440·768·390 너비로 주요 화면을 찍고 가로 넘침을 `overflow.json`에 남김. 기본 E2E에는 들어가지 않음

```bash
CAPTURE_DIR=../../../design/ui-draft/impl-screenshots-v2 npx playwright test -c playwright.capture.config.ts
```

## 알려진 한계

- 토큰 두 개를 localStorage에 둠(1차 결정). 화면에 악성 스크립트가 끼어들면 토큰을 가져갈 수 있어, 빌드 결과에 CSP(`script-src 'self'`)를 넣고 외부 스크립트를 쓰지 않음. 리프레시 토큰 쿠키 방식은 2차
- 같은 회원이 다른 브라우저에서 로그인하면 먼저 쓰던 쪽의 재발급이 실패함(백엔드 리프레시 토큰이 회원당 하나). 기기별 토큰은 2차
