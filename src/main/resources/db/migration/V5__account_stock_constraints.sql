-- 외래키와 CHECK는 값이 NULL이면 통과시키므로 제약이 걸린 칸을 함께 NOT NULL로 막음
ALTER TABLE `account`
  MODIFY COLUMN `member_id` bigint NOT NULL,
  ADD CONSTRAINT `fk_account_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  ADD CONSTRAINT `chk_account_balance` CHECK (`balance` >= 0),
  ADD CONSTRAINT `chk_account_held` CHECK (`held_amount` >= 0),
  ADD CONSTRAINT `chk_account_held_within_balance` CHECK (`held_amount` <= `balance`);

-- 수량이 0이 된 보유 내역은 남기지 않고 지움
ALTER TABLE `stock_balance`
  ADD CONSTRAINT `fk_stock_balance_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`),
  ADD CONSTRAINT `fk_stock_balance_stock` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`),
  ADD CONSTRAINT `chk_stock_balance_quantity` CHECK (`quantity` > 0),
  ADD CONSTRAINT `chk_stock_balance_held` CHECK (`held_quantity` >= 0),
  ADD CONSTRAINT `chk_stock_balance_held_within_quantity` CHECK (`held_quantity` <= `quantity`),
  ADD CONSTRAINT `chk_stock_balance_avg_price` CHECK (`avg_price` >= 0);

-- 보낸 쪽 이체 기록의 묶음 번호는 자기 id라 처음 저장할 때 비어 있으므로, 받는 쪽(수량 양수)만 반드시 차야 함
ALTER TABLE `trading`
  MODIFY COLUMN `account_id` bigint NOT NULL,
  MODIFY COLUMN `stock_id` bigint NOT NULL,
  MODIFY COLUMN `type` varchar(255) NOT NULL,
  MODIFY COLUMN `status` varchar(255) NOT NULL,
  ADD CONSTRAINT `fk_trading_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`),
  ADD CONSTRAINT `fk_trading_stock` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`),
  ADD CONSTRAINT `chk_trading_transfer` CHECK (
    (`type` COLLATE utf8mb4_bin IN ('BUY', 'SELL') AND `stock_transfer_id` IS NULL)
    OR (`type` COLLATE utf8mb4_bin = 'TRANSFER' AND (`quantity` < 0 OR `stock_transfer_id` IS NOT NULL))),
  ADD CONSTRAINT `chk_trading_fill` CHECK (
    (`status` COLLATE utf8mb4_bin IN ('PENDING', 'CANCELLED') AND `filled_price` IS NULL AND `trade_at` IS NULL)
    OR (`status` COLLATE utf8mb4_bin = 'FILLED' AND `filled_price` IS NOT NULL AND `trade_at` IS NOT NULL));
