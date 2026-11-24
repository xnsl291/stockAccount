-- 회원 돈의 반대편을 적는 시스템 계정. 잔액 칸을 두면 모든 거래가 그 행을 잠그려고 줄을 서므로 기록 합계로 계산함
CREATE TABLE `system_account` (
  `code` varchar(30) NOT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `system_account` (`code`, `name`) VALUES
  ('EXTERNAL_CASH', '외부 입출금'),
  ('TRADE_COUNTERPARTY', '매매 상대'),
  ('FEE_INCOME', '수수료 수입');

-- 시스템 기록이 거래의 종류와 금액까지 외래키로 물게 하려면 가리킬 칸 묶음에 유니크 키가 있어야 함
ALTER TABLE `transaction`
  ADD CONSTRAINT `uk_transaction_system_entry` UNIQUE (`id`, `type`, `amount`);

-- 종류와 금액을 거래와 같은 값으로만 적을 수 있고 반대 금액은 DB가 계산하므로, 금액이나 계정이 어긋난 줄이 생기지 않음
CREATE TABLE `system_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transaction_id` bigint NOT NULL,
  `transaction_type` varchar(255) NOT NULL,
  `transaction_amount` bigint NOT NULL,
  `system_account` varchar(30) NOT NULL,
  `amount` bigint GENERATED ALWAYS AS (-`transaction_amount`) STORED NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_entry_transaction` (`transaction_id`),
  CONSTRAINT `fk_system_entry_transaction` FOREIGN KEY (`transaction_id`, `transaction_type`, `transaction_amount`)
    REFERENCES `transaction` (`id`, `type`, `amount`),
  CONSTRAINT `fk_system_entry_system_account` FOREIGN KEY (`system_account`) REFERENCES `system_account` (`code`),
  CONSTRAINT `chk_system_entry_account` CHECK (
    (`transaction_type` COLLATE utf8mb4_bin IN ('DEPOSIT', 'WITHDRAW') AND `system_account` COLLATE utf8mb4_bin = 'EXTERNAL_CASH')
    OR (`transaction_type` COLLATE utf8mb4_bin IN ('STOCK_BUY', 'STOCK_SELL') AND `system_account` COLLATE utf8mb4_bin = 'TRADE_COUNTERPARTY')
    OR (`transaction_type` COLLATE utf8mb4_bin = 'FEE' AND `system_account` COLLATE utf8mb4_bin = 'FEE_INCOME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 이미 쌓인 거래에도 반대 기록을 채워, 이 버전부터가 아니라 처음부터 전체 합이 0이 되게 함
INSERT INTO `system_entry` (`transaction_id`, `transaction_type`, `transaction_amount`, `system_account`, `created_at`)
SELECT `id`, `type`, `amount`,
       CASE WHEN `type` IN ('DEPOSIT', 'WITHDRAW') THEN 'EXTERNAL_CASH'
            WHEN `type` IN ('STOCK_BUY', 'STOCK_SELL') THEN 'TRADE_COUNTERPARTY'
            ELSE 'FEE_INCOME' END,
       COALESCE(`transacted_at`, NOW(6))
FROM `transaction`
WHERE `type` NOT IN ('TRANSFER_OUT', 'TRANSFER_IN')
ORDER BY `id`;
