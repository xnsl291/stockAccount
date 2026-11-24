package zb.accountMangement.account;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래 기록이 자기를 만든 주문·이체·송금을 가리키는지
 */
class LedgerCauseTest extends ApiTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 체결_대금과_수수료는_바로_체결이든_시세_갱신_체결이든_그_주문을_가리킨다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock stock = saveStock(1_000);
		long buyId = order("buy", member, account, stock, 1_000, "FILLED");
		long sellId = order("sell", member, account, stock, 1_500, "PENDING");
		updatePrice(stock, 1_500);

		assertThat(ledgerOf(account)).containsExactly(
				"DEPOSIT - - - -",
				"STOCK_BUY " + buyId + " - - -",
				"FEE " + buyId + " - - -",
				"STOCK_SELL " + sellId + " - - -",
				"FEE " + sellId + " - - -");
	}

	@Test
	void 송금_두_줄은_같은_송금_묶음_번호와_서로의_계좌를_가지고_송금마다_번호가_다르다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Account other = saveAccount(saveMember());

		transferCash(member, account, other, 1_000);
		transferCash(member, account, other, 1_000);

		List<String> sent = jdbcTemplate.queryForList(
				"select cash_transfer_id from transaction where account_id = ? and type = 'TRANSFER_OUT' order by id",
				String.class, account.getId());
		assertThat(sent).hasSize(2).doesNotContainNull().doesNotHaveDuplicates();
		assertThat(ledgerOf(account)).containsExactly(
				"DEPOSIT - - - -",
				"TRANSFER_OUT - - " + sent.get(0) + " " + other.getId(),
				"TRANSFER_OUT - - " + sent.get(1) + " " + other.getId());
		assertThat(ledgerOf(other)).containsExactly(
				"TRANSFER_IN - - " + sent.get(0) + " " + account.getId(),
				"TRANSFER_IN - - " + sent.get(1) + " " + account.getId());
	}

	@Test
	void 주식_이체_두_줄과_수수료는_보낸_쪽_이체_기록의_번호로_묶인다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Account other = saveAccount(saveMember());
		Stock stock = saveStock(1_000);
		order("buy", member, account, stock, 1_000, "FILLED");

		postJson("/api/stocks/transfer", member, Map.of("senderAccountId", account.getId(), "receiverAccountId", other.getId(),
				"stockId", stock.getId(), "quantity", 4, "accountPassword", ACCOUNT_PASSWORD)).andExpect(status().isOk());

		Long sentId = jdbcTemplate.queryForObject(
				"select id from trading where account_id = ? and type = 'TRANSFER'", Long.class, account.getId());
		assertThat(jdbcTemplate.queryForList(
				"select quantity from trading where stock_transfer_id = ? order by id", Integer.class, sentId))
				.containsExactly(-4, 4);
		assertThat(ledgerOf(account)).last().isEqualTo("FEE - " + sentId + " - -");
	}

	private long order(String side, Member member, Account account, Stock stock, long price, String expectedStatus) throws Exception {
		String body = postJson("/api/stocks/" + side, member, Map.of("accountId", account.getId(), "stockId", stock.getId(),
				"price", price, "quantity", 10, "accountPassword", ACCOUNT_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(expectedStatus))
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asLong();
	}

	private void updatePrice(Stock stock, long price) throws Exception {
		mockMvc.perform(as(saveAdmin(), put("/admin/stocks/" + stock.getId() + "/price"))
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(Map.of("price", price))))
				.andExpect(status().isOk());
	}

	private void transferCash(Member member, Account from, Account to, long amount) throws Exception {
		postJson("/api/transactions/" + from.getId() + "/transfer/" + to.getId(), member,
				Map.of("amount", amount, "accountPassword", ACCOUNT_PASSWORD)).andExpect(status().isOk());
	}

	private List<String> ledgerOf(Account account) {
		return jdbcTemplate.queryForList("select concat_ws(' ', type, coalesce(order_id, '-'), coalesce(stock_transfer_id, '-'),"
				+ " coalesce(cash_transfer_id, '-'), coalesce(counterparty_account_id, '-'))"
				+ " from transaction where account_id = ? order by id", String.class, account.getId());
	}
}
