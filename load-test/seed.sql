-- 부하 측정용 데이터. 측정마다 같은 출발점에서 시작하도록 업무 테이블을 비우고 다시 넣음
-- 회원 1,003명(관리자 1, 일반 1,000, 집중 시나리오용 2), 계좌 1,002개, 종목 20개, 보유 내역 5,000건, 거래 기록 10,002건
-- 로그인 비밀번호는 모두 password123, 계좌 비밀번호는 모두 1234 (BCrypt 해시를 미리 만들어 넣음)
-- 계좌 잔액은 거래 기록(입금)을 더한 값과 같게 넣어, 측정 뒤 "잔액 = 거래 기록 합계" 확인의 출발점이 맞게 함

SET SESSION cte_max_recursion_depth = 20000;
TRUNCATE TABLE `transaction`;
TRUNCATE TABLE trading;
TRUNCATE TABLE stock_balance;
TRUNCATE TABLE stock;
TRUNCATE TABLE idempotency_record;
TRUNCATE TABLE account;
TRUNCATE TABLE member;

SET @login_pw = '$2a$10$uwcDJclOLgtrPZzNA7rA5uDJul13o60BZBpqewsRdIw30eptD1zlS';
SET @account_pw = '$2a$10$NewfmPKv3C/EZ8qhJg8jmuL03zGYTpNKXeUpm313fVakNBKm7720S';

-- 회원: 1번 관리자, 2~1001번 일반 회원, 1002·1003번 집중 시나리오 회원
INSERT INTO member (id, name, phone_number, password, role, created_at)
VALUES (1, 'admin', '01099990000', @login_pw, 'ADMIN', NOW(6));
INSERT INTO member (id, name, phone_number, password, role, created_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1000)
SELECT i + 1, CONCAT('user', i), CONCAT('010', LPAD(90000000 + i, 8, '0')), @login_pw, 'USER', NOW(6) FROM n;
INSERT INTO member (id, name, phone_number, password, role, created_at) VALUES
  (1002, 'hot-withdraw', '01098880001', @login_pw, 'USER', NOW(6)),
  (1003, 'hot-order', '01098880002', @login_pw, 'USER', NOW(6));

-- 계좌: 1~1000번은 일반 회원 계좌(잔액 1억), 1001번은 출금 집중용, 1002번은 주문 집중용(각 잔액 100만)
INSERT INTO account (id, member_id, nickname, password, password_fail_count, account_number, balance, held_amount, status, created_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1000)
SELECT i, i + 1, CONCAT('acc', i), @account_pw, 0, CONCAT('1000', LPAD(i, 10, '0')), 100000000, 0, 'EXISTS', NOW(6) FROM n;
INSERT INTO account (id, member_id, nickname, password, password_fail_count, account_number, balance, held_amount, status, created_at) VALUES
  (1001, 1002, 'hot-withdraw', @account_pw, 0, '20000000000001', 1000000, 0, 'EXISTS', NOW(6)),
  (1002, 1003, 'hot-order', @account_pw, 0, '20000000000002', 1000000, 0, 'EXISTS', NOW(6));

-- 거래 기록: 일반 계좌마다 1,000만 원 입금 10건, 집중용 계좌는 100만 원 입금 1건
INSERT INTO `transaction` (account_id, name, type, amount, memo, balance_after, transacted_at)
WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 9999)
SELECT FLOOR(i / 10) + 1, 'seed', 'DEPOSIT', 10000000, NULL, (MOD(i, 10) + 1) * 10000000,
       NOW(6) - INTERVAL (10 - MOD(i, 10)) DAY FROM n;
INSERT INTO `transaction` (account_id, name, type, amount, memo, balance_after, transacted_at) VALUES
  (1001, 'seed', 'DEPOSIT', 1000000, NULL, 1000000, NOW(6)),
  (1002, 'seed', 'DEPOSIT', 1000000, NULL, 1000000, NOW(6));

-- 종목 20개. 현재가 11,000 ~ 30,000원
INSERT INTO stock (id, code, name, current_price)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20)
SELECT i, CONCAT('9000', LPAD(i, 2, '0')), CONCAT('부하종목', i), 10000 + i * 1000 FROM n;

-- 보유 내역: 일반 계좌마다 1~5번 종목을 100주씩. 매도 주문을 낼 수 있게 함
INSERT INTO stock_balance (account_id, stock_id, avg_price, quantity, held_quantity)
WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 4999)
SELECT FLOOR(i / 5) + 1, MOD(i, 5) + 1, 10000 + (MOD(i, 5) + 1) * 1000, 100, 0 FROM n;
