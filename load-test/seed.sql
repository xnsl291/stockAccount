-- 부하 측정용 데이터. 측정마다 같은 출발점에서 시작하도록 업무 테이블을 비우고 다시 넣음
-- 회원 1,003명(관리자 1, 일반 1,000, 집중 시나리오용 2), 계좌 1,002개, 종목 20개, 체결 주문 5,000건, 보유 내역 5,000건, 거래 기록 20,002건
-- 로그인 비밀번호는 모두 password123, 계좌 비밀번호는 모두 1234 (BCrypt 해시를 미리 만들어 넣음)
-- 잔액은 거래 기록에서, 보유 내역은 체결 주문에서, 시스템 기록은 거래 기록에서 계산해 넣음

SET SESSION cte_max_recursion_depth = 20000;
-- 외래키가 가리키는 테이블은 비어 있어도 TRUNCATE가 거절되므로 비우는 동안만 검사를 끔
SET SESSION foreign_key_checks = 0;
TRUNCATE TABLE system_entry;
TRUNCATE TABLE `transaction`;
TRUNCATE TABLE trading;
TRUNCATE TABLE stock_balance;
TRUNCATE TABLE stock;
TRUNCATE TABLE idempotency_record;
TRUNCATE TABLE account;
TRUNCATE TABLE member;
SET SESSION foreign_key_checks = 1;

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

-- 계좌: 1~1000번은 일반 회원 계좌, 1001번은 출금 집중용, 1002번은 주문 집중용
INSERT INTO account (id, member_id, nickname, password, password_fail_count, account_number, balance, held_amount, status, created_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1000)
SELECT i, i + 1, CONCAT('acc', i), @account_pw, 0, CONCAT('1000', LPAD(i, 10, '0')), 0, 0, 'EXISTS', NOW(6) FROM n;
INSERT INTO account (id, member_id, nickname, password, password_fail_count, account_number, balance, held_amount, status, created_at) VALUES
  (1001, 1002, 'hot-withdraw', @account_pw, 0, '20000000000001', 0, 0, 'EXISTS', NOW(6)),
  (1002, 1003, 'hot-order', @account_pw, 0, '20000000000002', 0, 0, 'EXISTS', NOW(6));

-- 종목 20개. 현재가 11,000 ~ 30,000원
INSERT INTO stock (id, code, name, current_price)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20)
SELECT i, CONCAT('9000', LPAD(i, 2, '0')), CONCAT('부하종목', i), 10000 + i * 1000 FROM n;

-- 체결 주문: 일반 계좌마다 1~5번 종목을 100주씩 매수. 매도 주문을 낼 수 있게 함
INSERT INTO trading (id, account_id, stock_id, type, status, price, quantity, filled_price, created_at, trade_at)
WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 4999)
SELECT i + 1, FLOOR(i / 5) + 1, MOD(i, 5) + 1, 'BUY', 'FILLED', 11000 + MOD(i, 5) * 1000, 100, 11000 + MOD(i, 5) * 1000,
       NOW(6) - INTERVAL 10 DAY, NOW(6) - INTERVAL 10 DAY FROM n;

-- 거래 기록: 일반 계좌마다 20건. 첫 입금 16,500,975원에서 매수 대금 650만 원과 수수료 975원(0.015%, 원 미만 버림)이 나가
-- 1,000만 원이 남고, 이어서 1,000만 원씩 9번 입금해 1억 원이 됨. 집중용 계좌는 100만 원 입금 1건
INSERT INTO `transaction` (account_id, name, type, amount, memo, balance_after, transacted_at, order_id)
WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 19999),
step AS (
  SELECT FLOOR(i / 20) + 1 AS account_id, MOD(i, 20) AS s FROM n
),
line AS (
  SELECT p.account_id, p.s, o.id AS order_id, k.name AS stock_name,
         CASE WHEN p.s NOT BETWEEN 1 AND 10 THEN 'DEPOSIT' WHEN MOD(p.s, 2) = 1 THEN 'STOCK_BUY' ELSE 'FEE' END AS type,
         CASE WHEN p.s = 0 THEN 16500975
              WHEN p.s > 10 THEN 10000000
              WHEN MOD(p.s, 2) = 1 THEN -o.filled_price * o.quantity
              ELSE -FLOOR(o.filled_price * o.quantity * 0.00015) END AS amount
  FROM step p
  LEFT JOIN trading o ON p.s BETWEEN 1 AND 10 AND o.id = (p.account_id - 1) * 5 + CEIL(p.s / 2)
  LEFT JOIN stock k ON k.id = o.stock_id
)
SELECT account_id,
       CASE type WHEN 'DEPOSIT' THEN 'seed' WHEN 'STOCK_BUY' THEN stock_name ELSE '주식 매매 수수료' END,
       type, amount, IF(type = 'FEE', stock_name, NULL),
       SUM(amount) OVER (PARTITION BY account_id ORDER BY s),
       NOW(6) - INTERVAL IF(s <= 10, 10, 20 - s) DAY,
       order_id
FROM line ORDER BY account_id, s;
INSERT INTO `transaction` (account_id, name, type, amount, memo, balance_after, transacted_at) VALUES
  (1001, 'seed', 'DEPOSIT', 1000000, NULL, 1000000, NOW(6)),
  (1002, 'seed', 'DEPOSIT', 1000000, NULL, 1000000, NOW(6));

UPDATE account a
JOIN (SELECT account_id, SUM(amount) AS total FROM `transaction` GROUP BY account_id) t ON t.account_id = a.id
SET a.balance = t.total;

INSERT INTO stock_balance (account_id, stock_id, avg_price, quantity, held_quantity)
SELECT account_id, stock_id, filled_price, quantity, 0 FROM trading ORDER BY id;

INSERT INTO system_entry (transaction_id, transaction_type, transaction_amount, system_account, created_at)
SELECT id, type, amount,
       CASE WHEN type IN ('DEPOSIT', 'WITHDRAW') THEN 'EXTERNAL_CASH'
            WHEN type IN ('STOCK_BUY', 'STOCK_SELL') THEN 'TRADE_COUNTERPARTY'
            ELSE 'FEE_INCOME' END,
       transacted_at
FROM `transaction` WHERE type NOT IN ('TRANSFER_OUT', 'TRANSFER_IN') ORDER BY id;
