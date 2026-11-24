package zb.accountMangement.common;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱을 거치지 않고 SQL로 고쳐도 깨진 행이 들어가지 못하는지.
 * 거절한 제약의 이름까지 봐, 그 제약을 빼면 다른 제약에 걸리더라도 해당 줄이 실패하게 함
 */
@Transactional
class DatabaseConstraintTest extends ApiTest {

	// 칸 순서: 주문, 주식 이체, 송금 묶음, 상대 계좌. 1은 찬 칸
	private static final Map<TransactionType, String> ALLOWED_CAUSES = Map.of(
			TransactionType.DEPOSIT, "0000",
			TransactionType.WITHDRAW, "0000",
			TransactionType.TRANSFER_OUT, "0011",
			TransactionType.TRANSFER_IN, "0011",
			TransactionType.STOCK_BUY, "1000",
			TransactionType.STOCK_SELL, "1000",
			TransactionType.FEE, "1000 0100");

	@Autowired
	JdbcTemplate jdbcTemplate;

	long accountId;
	long otherAccountId;
	long stockId;
	long orderId;
	long transferId;

	@BeforeEach
	void 올바른_행을_넣어_둔다() {
		Member member = saveMember();
		accountId = saveAccount(member).getId();
		otherAccountId = saveAccount(member).getId();
		stockId = saveStock(1_000).getId();
		jdbcTemplate.update("update account set balance = 1000, held_amount = 400 where id = ?", accountId);
		jdbcTemplate.update("insert into stock_balance (account_id, stock_id, avg_price, quantity, held_quantity) values (?, ?, 1000, 10, 4)",
				accountId, stockId);
		orderId = insertTrading("BUY", "PENDING", null, null);
		transferId = insertTrading("TRANSFER", "FILLED", 1_000L, "2026-01-01");
		jdbcTemplate.update("update trading set stock_transfer_id = id where id = ?", transferId);
	}

	@Test
	void 거래_종류마다_정해진_원인_칸_조합만_들어간다() {
		for (TransactionType type : TransactionType.values()) {
			for (int bits = 0; bits < 16; bits++) {
				String filled = String.format("%4s", Integer.toBinaryString(bits)).replace(' ', '0');
				String violated = violatedBy(() -> insertTransaction(type, filled));
				if (ALLOWED_CAUSES.get(type).contains(filled))
					assertThat(violated).as(type + " " + filled).isEmpty();
				else
					assertThat(violated).as(type + " " + filled).contains("'chk_transaction_cause'");
			}
		}
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '~', value = {
			"update account set member_id = -1 where id = :account | `fk_account_member`",
			"update account set member_id = null where id = :account | Column 'member_id' cannot be null",
			"update account set balance = -1, held_amount = 0 where id = :account | 'chk_account_balance'",
			"update account set held_amount = -1 where id = :account | 'chk_account_held'",
			"update account set held_amount = 1001 where id = :account | 'chk_account_held_within_balance'",
			"update account set balance = 399 where id = :account | 'chk_account_held_within_balance'",
			"update stock_balance set account_id = -1 where account_id = :account | `fk_stock_balance_account`",
			"update stock_balance set stock_id = -1 where account_id = :account | `fk_stock_balance_stock`",
			"update stock_balance set quantity = 0, held_quantity = 0 where account_id = :account | 'chk_stock_balance_quantity'",
			"update stock_balance set held_quantity = -1 where account_id = :account | 'chk_stock_balance_held'",
			"update stock_balance set held_quantity = 11 where account_id = :account | 'chk_stock_balance_held_within_quantity'",
			"update stock_balance set avg_price = -1 where account_id = :account | 'chk_stock_balance_avg_price'",
			"update trading set account_id = -1 where id = :order | `fk_trading_account`",
			"update trading set account_id = null where id = :order | Column 'account_id' cannot be null",
			"update trading set stock_id = -1 where id = :order | `fk_trading_stock`",
			"update trading set stock_id = null where id = :order | Column 'stock_id' cannot be null",
			"update trading set stock_transfer_id = -1 where id = :transfer | `fk_trading_stock_transfer`",
			"update trading set type = null where id = :order | Column 'type' cannot be null",
			"update trading set type = 'GIFT' where id = :order | 'chk_trading_transfer'",
			"update trading set stock_transfer_id = :transfer where id = :order | 'chk_trading_transfer'",
			"update trading set status = null where id = :order | Column 'status' cannot be null",
			"update trading set status = 'GONE' where id = :order | 'chk_trading_fill'",
			"update trading set filled_price = 1 where id = :order | 'chk_trading_fill'",
			"update trading set trade_at = now() where id = :order | 'chk_trading_fill'",
			"update trading set status = 'CANCELLED', filled_price = 1, trade_at = now() where id = :order | 'chk_trading_fill'",
			"update trading set filled_price = null where id = :transfer | 'chk_trading_fill'",
			"update trading set trade_at = null where id = :transfer | 'chk_trading_fill'",
			"insert into transaction (account_id, type, amount, balance_after) values (-1, 'DEPOSIT', 1, 1) | `fk_transaction_account`",
			"insert into transaction (account_id, type, amount, balance_after) values (null, 'DEPOSIT', 1, 1) | Column 'account_id' cannot be null",
			"insert into transaction (account_id, type, amount, balance_after) values (:account, null, 1, 1) | Column 'type' cannot be null",
			"insert into transaction (account_id, type, amount, balance_after) values (:account, 'GIFT', 1, 1) | 'chk_transaction_cause'",
			"insert into transaction (account_id, type, amount, balance_after, order_id) values (:account, 'STOCK_BUY', 1, 1, -1) | `fk_transaction_order`",
			"insert into transaction (account_id, type, amount, balance_after, stock_transfer_id) values (:account, 'FEE', 1, 1, -1) | `fk_transaction_stock_transfer`",
			"insert into transaction (account_id, type, amount, balance_after, cash_transfer_id, counterparty_account_id) values (:account, 'TRANSFER_IN', 1, 1, 'x', -1) | `fk_transaction_counterparty`",
	})
	void 제약을_어기는_SQL은_그_제약에_걸린다(String sql, String constraint) {
		assertThat(violatedBy(() -> execute(sql))).contains(constraint);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"update account set held_amount = balance where id = :account",
			"update account set balance = 0, held_amount = 0 where id = :account",
			"update stock_balance set held_quantity = quantity, avg_price = 0 where account_id = :account",
			"update trading set status = 'CANCELLED' where id = :order",
	})
	void 한도에_걸친_값은_들어간다(String sql) {
		assertThat(execute(sql)).isEqualTo(1);
	}

	private int execute(String sql) {
		return jdbcTemplate.update(sql
				.replace(":account", String.valueOf(accountId))
				.replace(":order", String.valueOf(orderId))
				.replace(":transfer", String.valueOf(transferId)));
	}

	private long insertTrading(String type, String status, Long filledPrice, String tradeAt) {
		jdbcTemplate.update("insert into trading (account_id, stock_id, type, status, price, quantity, filled_price, trade_at)"
				+ " values (?, ?, ?, ?, 1000, 1, ?, ?)", accountId, stockId, type, status, filledPrice, tradeAt);
		return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
	}

	private void insertTransaction(TransactionType type, String filled) {
		jdbcTemplate.update("insert into transaction (account_id, type, amount, balance_after,"
						+ " order_id, stock_transfer_id, cash_transfer_id, counterparty_account_id) values (?, ?, 1, 1, ?, ?, ?, ?)",
				accountId, type.name(),
				filled.charAt(0) == '1' ? orderId : null,
				filled.charAt(1) == '1' ? transferId : null,
				filled.charAt(2) == '1' ? "7d0f3c1e-2b7a-4a57-9a43-0f6f6b1e0c11" : null,
				filled.charAt(3) == '1' ? otherAccountId : null);
	}

	private String violatedBy(Runnable sql) {
		try {
			sql.run();
			return "";
		} catch (DataAccessException e) {
			return e.getMostSpecificCause().getMessage();
		}
	}
}
