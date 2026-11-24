-- 거래 기록이 자기를 만든 주문·주식 이체·송금을 가리키게 함.
-- 송금 묶음 번호는 가리킬 테이블이 없어 외래키 없이 UUID로 둠.
-- 주식 이체 묶음 번호는 보낸 쪽 이체 기록의 id이고, 보낸 쪽 줄도 자기 id를 가짐
ALTER TABLE `transaction`
  ADD COLUMN `order_id` bigint DEFAULT NULL,
  ADD COLUMN `stock_transfer_id` bigint DEFAULT NULL,
  ADD COLUMN `cash_transfer_id` char(36) DEFAULT NULL,
  ADD COLUMN `counterparty_account_id` bigint DEFAULT NULL,
  ADD CONSTRAINT `fk_transaction_order` FOREIGN KEY (`order_id`) REFERENCES `trading` (`id`),
  ADD CONSTRAINT `fk_transaction_stock_transfer` FOREIGN KEY (`stock_transfer_id`) REFERENCES `trading` (`id`),
  ADD CONSTRAINT `fk_transaction_counterparty` FOREIGN KEY (`counterparty_account_id`) REFERENCES `account` (`id`);

ALTER TABLE `trading`
  ADD COLUMN `stock_transfer_id` bigint DEFAULT NULL,
  ADD CONSTRAINT `fk_trading_stock_transfer` FOREIGN KEY (`stock_transfer_id`) REFERENCES `trading` (`id`);
