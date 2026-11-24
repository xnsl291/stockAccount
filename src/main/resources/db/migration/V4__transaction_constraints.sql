-- 거래 종류마다 반드시 차야 하는 원인 칸과 비어야 하는 원인 칸을 정함.
-- CHECK는 결과가 NULL이어도 통과시키므로 type을 NOT NULL로 막아야 종류가 빈 줄이 새지 않음
ALTER TABLE `transaction`
  MODIFY COLUMN `account_id` bigint NOT NULL,
  MODIFY COLUMN `type` varchar(255) NOT NULL,
  ADD CONSTRAINT `fk_transaction_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`),
  ADD CONSTRAINT `chk_transaction_cause` CHECK (
    (`type` IN ('DEPOSIT', 'WITHDRAW')
      AND `order_id` IS NULL AND `stock_transfer_id` IS NULL
      AND `cash_transfer_id` IS NULL AND `counterparty_account_id` IS NULL)
    OR (`type` IN ('TRANSFER_OUT', 'TRANSFER_IN')
      AND `order_id` IS NULL AND `stock_transfer_id` IS NULL
      AND `cash_transfer_id` IS NOT NULL AND `counterparty_account_id` IS NOT NULL)
    OR (`type` IN ('STOCK_BUY', 'STOCK_SELL')
      AND `order_id` IS NOT NULL AND `stock_transfer_id` IS NULL
      AND `cash_transfer_id` IS NULL AND `counterparty_account_id` IS NULL)
    OR (`type` = 'FEE'
      AND (`order_id` IS NULL) <> (`stock_transfer_id` IS NULL)
      AND `cash_transfer_id` IS NULL AND `counterparty_account_id` IS NULL));
