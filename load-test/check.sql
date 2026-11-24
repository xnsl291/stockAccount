-- 측정 뒤 금액·수량이 맞는지 확인. 불일치 열이 모두 0이어야 함
-- 대사 기능이 생기기 전이라 기존 테스트가 하는 합계 비교를 SQL로 대신함

SELECT '잔액과 거래 기록 합계가 다른 계좌' AS 검사, COUNT(*) AS 불일치
FROM account a
LEFT JOIN (SELECT account_id, SUM(amount) AS total FROM `transaction` GROUP BY account_id) t ON t.account_id = a.id
WHERE a.balance <> COALESCE(t.total, 0)
UNION ALL
SELECT '잔액 음수, 묶인 금액 음수, 잔액 < 묶인 금액', COUNT(*)
FROM account WHERE balance < 0 OR held_amount < 0 OR balance < held_amount
UNION ALL
-- 미체결 매수 주문이 묶는 금액 = 주문가 x 수량 + 수수료(0.015%, 원 미만 버림)
SELECT '묶인 금액과 미체결 매수 주문 합계가 다른 계좌', COUNT(*)
FROM account a
LEFT JOIN (SELECT account_id, SUM(price * quantity + FLOOR(price * quantity * 0.00015)) AS hold
           FROM trading WHERE status = 'PENDING' AND type = 'BUY' GROUP BY account_id) o ON o.account_id = a.id
WHERE a.held_amount <> COALESCE(o.hold, 0)
UNION ALL
SELECT '보유 수량 음수, 묶인 수량 음수, 보유 < 묶인 수량', COUNT(*)
FROM stock_balance WHERE quantity < 0 OR held_quantity < 0 OR quantity < held_quantity
UNION ALL
SELECT '묶인 수량과 미체결 매도 주문 합계가 다른 보유 내역', COUNT(*)
FROM stock_balance b
LEFT JOIN (SELECT account_id, stock_id, SUM(quantity) AS q FROM trading
           WHERE status = 'PENDING' AND type = 'SELL' GROUP BY account_id, stock_id) o
  ON o.account_id = b.account_id AND o.stock_id = b.stock_id
WHERE b.held_quantity <> COALESCE(o.q, 0)
UNION ALL
-- 체결 한 번에 매수·매도 대금 거래 기록이 정확히 한 줄 생김. 개수가 다르면 중복 체결이나 기록 누락
SELECT '체결 주문 수와 매매 대금 거래 기록 수의 차이',
       ABS((SELECT COUNT(*) FROM trading WHERE status = 'FILLED')
         - (SELECT COUNT(*) FROM `transaction` WHERE type IN ('STOCK_BUY', 'STOCK_SELL')))
UNION ALL
-- 처음 보유 내역도 체결 주문에서 나온 것이라 따로 더하지 않음
SELECT '보유 수량과 (체결 매수 - 체결 매도)가 다른 종목', COUNT(*)
FROM (
  SELECT k.account_id, k.stock_id,
         COALESCE(b.quantity, 0) AS actual,
         COALESCE(f.bought, 0) - COALESCE(f.sold, 0) AS expected
  FROM (SELECT account_id, stock_id FROM stock_balance
        UNION SELECT account_id, stock_id FROM trading WHERE status = 'FILLED') k
  LEFT JOIN stock_balance b ON b.account_id = k.account_id AND b.stock_id = k.stock_id
  LEFT JOIN (SELECT account_id, stock_id,
                    SUM(CASE WHEN type = 'BUY' THEN quantity ELSE 0 END) AS bought,
                    SUM(CASE WHEN type = 'SELL' THEN quantity ELSE 0 END) AS sold
             FROM trading WHERE status = 'FILLED' GROUP BY account_id, stock_id) f
    ON f.account_id = k.account_id AND f.stock_id = k.stock_id
) q WHERE actual <> expected
UNION ALL
SELECT '시스템 기록이 없거나 금액이 반대가 아닌 거래, 시스템 기록이 있는 송금', COUNT(*)
FROM `transaction` t
LEFT JOIN system_entry e ON e.transaction_id = t.id
WHERE IF(t.type IN ('TRANSFER_OUT', 'TRANSFER_IN'), e.id IS NOT NULL, e.id IS NULL OR e.amount <> -t.amount)
UNION ALL
SELECT '회원 거래와 시스템 기록을 모두 더한 금액',
       ABS((SELECT COALESCE(SUM(amount), 0) FROM `transaction`) + (SELECT COALESCE(SUM(amount), 0) FROM system_entry));

-- 측정 뒤 데이터 건수
SELECT (SELECT COUNT(*) FROM `transaction`) AS 거래기록, (SELECT COUNT(*) FROM trading) AS 주문,
       (SELECT COUNT(*) FROM trading WHERE status = 'FILLED') AS 체결,
       (SELECT COUNT(*) FROM trading WHERE status = 'PENDING') AS 미체결,
       (SELECT COUNT(*) FROM trading WHERE status = 'CANCELLED') AS 취소,
       (SELECT COUNT(*) FROM idempotency_record) AS 멱등기록,
       (SELECT COUNT(*) FROM system_entry) AS 시스템기록;
