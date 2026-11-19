# stockAccount

계좌 입출금·송금과 주식 지정가 주문을 다루는 Spring Boot 백엔드. 회원가입부터 계좌 개설, 입금·출금·송금, 주식 매수·매도·취소·이체, 관리자의 종목 등록과 시세 갱신까지 REST API로 제공함

돈이 오가는 기능에서 생기기 쉬운 문제(동시 요청으로 잔액이 틀어지는 것, 같은 요청이 두 번 실행되는 것, 거래 기록과 잔액이 어긋나는 것)를 막는 데 중점을 둠

## 기술 스택

- Java 11(빌드 대상), Spring Boot 2.7.8, Spring Data JPA, Spring Security, JWT(jjwt 0.11.5)
- MySQL 8.0, Redis 7(문자 인증번호, 리프레시 토큰, 로그아웃한 토큰)
- CoolSMS(문자 발송, 로컬에서는 로그로 대체)
- 테스트: JUnit 5, MockMvc, Testcontainers(MySQL·Redis)

## 실행 방법

JDK 17에서 빌드·테스트를 확인했고, 컴파일 대상은 Java 11임. MySQL·Redis와 테스트용 컨테이너를 띄우려면 Docker가 있어야 함

```bash
# 1. MySQL·Redis 띄우기 (DB accountSystem, 계정 account/account)
docker compose up -d

# 2. 설정 (필수). JWT 비밀키가 없으면 서버가 뜨지 않음
cp application-secret.yml.example src/main/resources/application-secret.yml
openssl rand -base64 48   # 출력값을 application-secret.yml의 jwt.secret에 넣음 (또는 JWT_SECRET 환경변수로 지정)

# 3. 서버 실행 (8080)
./gradlew bootRun

# 4. 테스트 (Testcontainers가 MySQL·Redis 컨테이너를 직접 띄움. 테스트용 JWT 키는 테스트 코드가 넣으므로 설정 파일이 없어도 됨)
./gradlew test
```

설정은 환경변수로도 줄 수 있음

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 로컬 docker compose 값 | MySQL 접속 정보 |
| `REDIS_HOST`, `REDIS_PORT` | `localhost`, `6379` | Redis 접속 정보 |
| `JWT_SECRET` | 없음(필수) | Base64, 32바이트 이상. 환경변수나 `application-secret.yml`의 `jwt.secret` 중 하나로 넣어야 함 |
| `JWT_ACCESS_TOKEN_SECONDS`, `JWT_REFRESH_TOKEN_SECONDS` | 1800, 1209600 | 토큰 만료 시간 |
| `SMS_PROVIDER` | `log` | `log`는 문자를 보내지 않고 로그만 남김, `coolsms`는 실제 발송 |
| `COOLSMS_API_KEY`, `COOLSMS_SECRET_KEY`, `SMS_SENDER_PHONE_NUMBER` | 없음 | `coolsms`일 때만 필요 |

스키마는 Flyway가 `src/main/resources/db/migration`의 SQL 파일로 만들고, Hibernate는 엔티티와 스키마가 맞는지만 확인함(`ddl-auto: validate`). 스키마를 바꿀 때는 기존 파일을 고치지 않고 다음 번호의 파일(`V2__...sql`)을 추가함

Flyway 도입 전(`ddl-auto: update`)에 만든 로컬 DB가 있으면 지우고 새로 만들어야 함. 지킬 운영 데이터가 없어 기존 DB를 첫 버전으로 표시(baseline)하지 않고 빈 DB에서 첫 파일부터 적용하는 방식으로 정했음. 기존 DB에는 `flyway_schema_history`가 없어 Flyway가 테이블이 이미 있다며 기동을 멈춤

```bash
docker compose down -v && docker compose up -d
```

### 모니터링

`docker compose up -d`가 Prometheus(`127.0.0.1:9090`)와 Grafana(`127.0.0.1:3000`, 기본 계정 admin/admin, `GRAFANA_ADMIN_PASSWORD`로 바꿈)도 띄움. Grafana에 `StockAccount 기본` 대시보드(경로별 응답 시간 p95, 처리량, 응답 상태, DB 커넥션 풀 대기, JVM 메모리·GC, CPU)가 들어 있음

앱은 지표를 업무 포트(8080)가 아니라 관리 포트 `127.0.0.1:9081`의 `/actuator/prometheus`, `/actuator/health`로만 내보냄. 이 두 경로는 토큰 없이 열리지만 이 PC 밖에서는 닿지 않음. 다른 장비의 Prometheus가 긁어 가게 하려면 `MANAGEMENT_ADDRESS`, `MANAGEMENT_PORT`를 바꿈

### 관리자 계정

관리자 가입 API는 일부러 두지 않음. 로컬에서는 일반 회원으로 가입한 뒤 DB에서 권한을 바꾸고 다시 로그인함(권한은 토큰에 들어가므로 기존 토큰에는 반영되지 않음)

```sql
UPDATE member SET role = 'ADMIN' WHERE phone_number = '01012345678';
```

## API

`/api/auth`의 가입·로그인·재발급·문자 인증을 빼면 모두 `Authorization: Bearer <액세스 토큰>`이 필요함. 돈이나 주식이 움직이는 요청은 `Idempotency-Key` 헤더(요청마다 새로 만든 값, 64자 이하)가 필요함

### 인증

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/auth/send-code` | 핸드폰번호로 인증번호 발송. 10분 동안 같은 번호로는 3번, 같은 IP에서는 10번까지 |
| POST | `/api/auth/verify-phone` | 인증번호 확인. `purpose`(`SIGN_UP`, `PHONE_CHANGE`, `ACCOUNT_UNLOCK`)를 함께 보내고, 성공하면 10분 동안 그 번호로 그 용도의 작업을 한 번 할 수 있음 |
| POST | `/api/auth/sign-up` | 회원가입. 인증을 마친 번호만 가능하고, 초기 계좌가 함께 개설됨 |
| POST | `/api/auth/login` | 로그인. 액세스·리프레시 토큰 발급 |
| POST | `/api/auth/reissue` | 리프레시 토큰으로 토큰 재발급. 쓴 리프레시 토큰은 다시 못 쓰고, 같은 토큰으로 동시에 요청해도 한 번만 발급됨 |
| POST | `/api/auth/logout` | 로그아웃. 현재 토큰 무효화 |
| DELETE | `/api/auth/sign-out` | 탈퇴. 잔액이나 보유 주식이 남은 계좌가 있으면 거절하고, 없으면 계좌를 모두 해지함 |

### 회원

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/member/{member_id}` | 내 정보 조회 |
| PATCH | `/api/member/{member_id}` | 이름·핸드폰번호·로그인 비밀번호 수정. 현재 로그인 비밀번호(`currentPassword`)가 맞아야 하고, 번호를 바꾸려면 새 번호 인증 필요. 비밀번호를 바꾸면 리프레시 토큰이 지워짐 |
| POST | `/api/member/find-pw/{member_id}` | 비밀번호 재설정용 인증번호 발송 |
| PATCH | `/api/member/find-pw/{member_id}/confirm` | 인증번호 확인 후 로그인 비밀번호 재설정. 리프레시 토큰이 지워짐 |

### 계좌

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/accounts/open` | 계좌 개설 |
| GET | `/api/accounts` | 내 계좌 목록. 해지한 계좌는 빠짐 |
| GET | `/api/accounts/{account_id}` | 계좌 조회(잔액, 주문에 묶인 금액, 쓸 수 있는 금액) |
| PATCH | `/api/accounts/{account_id}` | 별칭·계좌 비밀번호 변경. 현재 계좌 비밀번호 필요 |
| POST | `/api/accounts/{account_id}/unlock` | 거래정지 계좌 잠금 해제. 등록된 번호로 문자 인증을 먼저 마쳐야 함 |
| DELETE | `/api/accounts/{account_id}` | 계좌 해지. 잔액·보유 주식이 없고 해지하지 않은 계좌가 2개 이상일 때만. 마지막 남은 계좌면 `LAST_ACCOUNT`, 거래정지 계좌면 `PENDING_ACCOUNT`로 거절 |
| GET | `/api/accounts/search/{account_number}` | 계좌번호로 검색. 내 계좌면 잔액까지, 남의 계좌면 계좌 ID와 소유주 이름만. 해지 계좌는 찾지 않음 |

### 계좌 거래

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/transactions/validation/{account_number}` | 받는 사람 이름 확인 |
| POST | `/api/transactions/deposit` | 입금 |
| POST | `/api/transactions/withdrawal` | 출금. 계좌 비밀번호 필요 |
| POST | `/api/transactions/{sender_account_id}/transfer/{receiver_account_id}` | 송금. 계좌 비밀번호 필요 |
| GET | `/api/transactions/{account_id}/history` | 거래내역. `type`, `from`·`to`(yyyy-MM-dd, 그날 포함), `page`, `size`(최대 100) |

### 주식

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/stocks?keyword=` | 종목코드·종목명으로 검색 |
| GET | `/api/stocks/{stock_id}` | 현재가 조회 |
| POST | `/api/stocks/buy` | 지정가 매수 주문. 계좌 비밀번호 필요 |
| POST | `/api/stocks/sell` | 지정가 매도 주문. 계좌 비밀번호 필요 |
| DELETE | `/api/stocks/{account_id}/orders/{order_id}` | 미체결 주문 취소 |
| POST | `/api/stocks/transfer` | 다른 계좌로 주식 이체. 계좌 비밀번호 필요 |
| GET | `/api/stocks/{account_id}/balance` | 보유 주식과 평가손익 |
| GET | `/api/stocks/{account_id}/history` | 주문·이체 내역. `status`, `year`, `month`로 걸러 봄 |

### 관리자 (ADMIN 권한)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/admin/stocks` | 종목 등록 |
| PUT | `/admin/stocks/{stock_id}/price` | 시세 갱신. 새 시세로 체결되는 미체결 주문을 함께 체결함 |

### 오류 응답

모든 오류는 같은 형식으로 돌려줌. 입력 검증에 걸리면 `errors`에 필드별 사유가 들어감. 예상하지 못한 예외는 서버 로그에 남기고 내부 내용 없이 500으로 응답함

```json
{ "timestamp": "...", "status": 400, "code": "UNMATCHED_PASSWORD", "message": "비밀번호가 일치하지 않습니다.", "errors": null }
```

## 설계 결정

- **금액은 원 단위 long.** 실수(double)는 0.1 같은 값을 정확히 표현하지 못해 더하고 빼다 보면 원 단위가 어긋남. 수수료처럼 비율을 곱하는 계산만 BigDecimal로 하고 원 미만은 버림. 금액 합계와 곱, 보유 수량(int)과 평균단가 계산이 범위를 넘으면 `Math.*Exact`가 막고 `AMOUNT_OUT_OF_RANGE`(400)로 응답함. 주문 수량은 한 번에 1,000,000주까지
- **잔액을 바꿀 때 계좌 행을 비관적 잠금(`SELECT ... FOR UPDATE`).** 잔액을 읽고 계산해서 쓰는 사이에 다른 요청이 끼어들지 못하게 함. 두 계좌를 함께 잠글 때(송금, 주식 이체, 탈퇴)는 항상 ID가 작은 계좌부터 잠가, 서로 반대 방향의 요청이 동시에 와도 교착 상태에 빠지지 않음. 해지·탈퇴 때 보유 주식이 남았는지도 잠그며 읽음. MySQL 기본 격리 수준(REPEATABLE READ)에서 잠그지 않고 읽으면 트랜잭션 처음의 스냅샷을 보므로, 그사이 커밋된 주식 이체를 못 보고 주식이 남은 계좌를 해지할 수 있기 때문임. 그래도 교착 상태나 잠금 대기 시간 초과가 나면 서버 오류(500)가 아니라 `CONCURRENT_REQUEST`(409)로 응답해 클라이언트가 다시 보내게 함. 서버가 알아서 다시 시도하지는 않음
- **Idempotency-Key와 유니크 제약.** 네트워크가 끊겨 클라이언트가 같은 요청을 다시 보내도 한 번만 실행되게, (회원, 키)에 유니크 제약을 건 테이블에 키를 작업보다 먼저 저장함. 같은 키가 다시 오면 처음 결과를 돌려주고, 같은 키를 다른 내용의 요청에 쓰면 409로 거절함. 작업이 실패하면 키도 롤백되어 다시 시도할 수 있음
- **거래 기록의 부호.** 잔액이 늘면 양수, 줄면 음수로 남기고 거래 후 잔액을 함께 저장함. 주식 매수·매도 대금과 수수료도 기록하므로, 한 계좌의 거래 금액을 모두 더하면 언제나 잔액과 같음
- **묶인 금액과 쓸 수 있는 금액.** 미체결 매수 주문은 주문가로 계산한 대금과 수수료만큼 돈을, 미체결 매도 주문은 수량만큼 주식을 묶음. 출금·송금·새 주문·주식 이체는 묶이지 않은 만큼만 할 수 있어, 같은 돈이나 주식으로 여러 번 주문할 수 없음
- **주문 상태 PENDING → FILLED / CANCELLED.** 주문을 받을 때 시세가 이미 조건을 만족하면 바로 체결하고, 아니면 PENDING으로 남김. 관리자가 시세를 바꾸면 그 종목의 미체결 주문을 먼저 들어온 순서로 다시 확인해 체결함. 체결 금액은 주문가가 아니라 그때 시세로 계산하고, 주문마다 트랜잭션을 따로 열어 한 건이 실패해도 나머지는 체결됨. PENDING 주문만 취소할 수 있고 취소하면 묶인 돈이나 주식이 풀림
- **수수료.** 매매 수수료는 체결 금액의 0.015%, 원 미만 버림. 주식 이체는 다른 회원에게 보낼 때만 보내는 쪽이 시세 기준 금액의 1%를 내고, 같은 회원의 계좌끼리는 무료. 받는 쪽 평균단가는 보내는 쪽 평균단가로 계산함
- **계좌 비밀번호 5회 오류 시 거래정지.** 맞히면 횟수를 0으로 되돌림. 정지된 계좌는 등록된 핸드폰번호로 문자 인증을 마쳐야 풀림. 문자 인증번호는 하나당 5번까지 확인할 수 있고, 5번째에 틀리면 그 인증번호를 폐기함. 새로 받은 인증번호는 다시 5번을 시도할 수 있지만 발송이 10분에 3번으로 묶여 있어 한 번호에 대입할 수 있는 횟수는 10분에 15번까지임. 발송 한도를 넘으면 `TOO_MANY_SMS_REQUESTS`(429)로 거절함. 틀린 횟수를 번호에 오래 묶어 두지 않는 이유는 남이 일부러 틀려서 본인의 비밀번호 재설정·잠금 해제·번호 변경을 막지 못하게 하려는 것임
- **응답은 dto로만.** 엔티티를 그대로 내보내지 않아 비밀번호 해시나 비밀번호 틀린 횟수가 응답에 섞이지 않음

## 동시성 검증 결과

잔액 50,000원 계좌에 10,000원 출금 10건을 동시에 보내면, 잠금을 넣기 전에는 10건 모두 성공하고 잔액이 40,000원으로 남았음(5회 반복 모두 같음). 비관적 잠금을 넣은 뒤에는 5건만 성공하고 잔액이 0원이 됨. 이 상황은 `TransactionConsistencyTest`에서 서비스 호출과 API 호출 두 가지로 확인함

받는 계좌를 잠그고 주식 보유 내역을 넣은 채 커밋하지 않은 트랜잭션(주식 이체와 같은 순서)을 열어 두고 그 회원이 탈퇴하면, 보유 주식을 잠그지 않고 읽던 때에는 탈퇴가 성공해 주식이 남은 계좌가 해지됐음. 잠그며 읽도록 고친 뒤에는 이체가 커밋되기를 기다렸다가 보유 주식을 보고 탈퇴를 거절함(`StockTransferTest`)

## 테스트

`./gradlew test`로 156개 테스트가 실제 MySQL·Redis 컨테이너 위에서 돌아감

- `TransactionConsistencyTest`: 동시 출금·맞송금, Idempotency-Key 재전송·동시 전송, 거래 기록 합계와 잔액 일치, 금액·계좌 상태 검증
- `TransactionHistoryTest`: 거래내역 페이지 나눔, 정렬, 기간·종류 필터
- `AccountSecurityTest`, `AccountManagementTest`: 남의 계좌 접근 차단, 계좌 비밀번호 오류와 거래정지(동시 오답과 변경 직후 옛 비밀번호 포함), 잠금 해제, 비밀번호 변경, 계좌번호 검색, 응답에 비밀번호가 없는지
- `StockOrderTest`, `StockTransferTest`: 체결·미체결·취소, 동시 주문, 시세 갱신 체결, 이체 수수료와 평균단가, 보유 내역 유니크 제약, 수량·평균단가 범위 초과, 주식 이체와 탈퇴가 겹칠 때
- `SignUpTest`, `MemberSecurityTest`, `JwtAuthenticationTest`: 가입·문자 인증(시도 횟수, 번호별·IP별 발송 횟수, 용도 구분)·번호 변경·탈퇴, 토큰 검증·만료·재발급·동시 재발급·권한, 비밀번호 변경 뒤 리프레시 토큰 무효화
- `ErrorResponseTest`: 금액 범위 초과, 스프링 MVC 예외의 오류 형식

## 구현하지 않은 것

예전 README에 적혀 있었지만 지금 코드에는 없는 기능

- 매도 대금의 D+2 정산(예수금 D+1·D+2 구분). 매도 대금은 체결 즉시 쓸 수 있음
- 친구 신청·목록, 친구 보유 주식 조회
- 시세 크롤링. 시세는 관리자가 API로 갱신함
- 관심 종목, 알림, 배당금 조회
- 비밀번호 오류 횟수의 시간 경과 자동 초기화. 문자 인증으로 잠금을 풀거나 비밀번호를 맞혀야 0이 됨

## 알려진 한계

- **주문 접수를 커밋한 직후 앱이 죽으면 체결될 주문이 다음 시세 갱신 때까지 PENDING으로 남을 수 있음.** 접수 중에 시세 갱신이 지나가도 놓치지 않도록 접수를 커밋한 뒤 체결 조건을 한 번 더 확인하는데, 커밋과 이 확인 사이에 앱이 멈추면 확인이 일어나지 않음
- **리프레시 토큰은 회원당 하나.** 새로 로그인하면 이전 기기의 리프레시 토큰은 더는 쓸 수 없음
- **남이 내 번호로 인증번호를 3번 받으면 10분 동안 나도 인증번호를 받을 수 없음.** 번호별 발송 한도를 공격자와 본인이 같이 쓰기 때문임. IP별 한도(10분에 10번)로 한 곳에서 여러 번호를 막는 것은 제한하지만, IP를 바꿔 가며 특정 번호를 노리면 막지 못함. 요청 IP는 `X-Forwarded-For`를 믿지 않고 접속한 주소를 쓰므로, 프록시 뒤에 둘 때는 프록시가 넘긴 주소를 믿도록 따로 설정해야 함
- **로그인 비밀번호 오류 횟수 제한이 없음.** 계좌 비밀번호와 문자 인증번호에만 횟수 제한이 있음
- **액세스 토큰은 만료(30분)까지 유효함.** 로그아웃·탈퇴 때는 그 요청에 쓴 토큰만 차단되고, 비밀번호를 바꾸면 리프레시 토큰만 지워짐. 다른 기기에서 받은 액세스 토큰은 만료될 때까지 쓸 수 있음. 회원 정보 API와 계좌 개설은 매번 회원 상태를 확인하고, 탈퇴한 회원의 계좌는 모두 해지돼 있어 거래는 막힘
- **입금 금액에는 상한이 없음.** 잔액이 long 범위를 넘으면 `AMOUNT_OUT_OF_RANGE`로 거절함
