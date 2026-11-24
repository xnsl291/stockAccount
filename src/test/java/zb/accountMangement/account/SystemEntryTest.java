package zb.accountMangement.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import zb.accountMangement.account.dto.DepositDto;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 돈이 움직일 때 시스템 계정에 반대 금액이 함께 적혀 모두 더하면 0이 되는지
 */
class SystemEntryTest extends ApiTest {

	@Autowired
	JdbcTemplate jdbcTemplate;
	@Autowired
	TransactionService transactionService;
	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void 회원끼리의_송금만_빼고_거래마다_시스템_계정에_반대_금액이_한_줄씩_남는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Account other = saveAccount(saveMember());
		Stock stock = saveStock(5_000);

		postJson("/api/transactions/withdrawal", member,
				Map.of("accountId", account.getId(), "amount", 1_000, "accountPassword", ACCOUNT_PASSWORD)).andExpect(status().isOk());
		order("buy", member, account, stock, 5_000, 10).andExpect(jsonPath("$.status").value("FILLED"));
		order("sell", member, account, stock, 8_000, 5).andExpect(jsonPath("$.status").value("PENDING"));
		mockMvc.perform(as(saveAdmin(), put("/admin/stocks/" + stock.getId() + "/price"))
				.contentType("application/json").content("{\"price\":8000}")).andExpect(status().isOk());
		transferCash(member, account, other, 3_000).andExpect(status().isOk());
		postJson("/api/stocks/transfer", member, Map.of("senderAccountId", account.getId(), "receiverAccountId", other.getId(),
				"stockId", stock.getId(), "quantity", 2, "accountPassword", ACCOUNT_PASSWORD)).andExpect(status().isOk());

		assertThat(entriesOf(account)).containsExactly(
				"DEPOSIT 100000 -> EXTERNAL_CASH -100000",
				"WITHDRAW -1000 -> EXTERNAL_CASH 1000",
				"STOCK_BUY -50000 -> TRADE_COUNTERPARTY 50000",
				"FEE -7 -> FEE_INCOME 7",
				"STOCK_SELL 40000 -> TRADE_COUNTERPARTY -40000",
				"FEE -6 -> FEE_INCOME 6",
				"TRANSFER_OUT -3000 -> 없음",
				"FEE -160 -> FEE_INCOME 160");
		assertThat(entriesOf(other)).containsExactly("TRANSFER_IN 3000 -> 없음");
		assertThat(totalOf(List.of(account, other))).isZero();
	}

	@Test
	void 입금과_체결과_송금이_동시에_몰려도_모두_처리되고_전체_합이_0이다() throws Exception {
		Stock stock = saveStock(10_000);
		Member member = saveMember();
		List<Account> accounts = new ArrayList<>();
		for (int i = 0; i < 4; i++)
			accounts.add(saveAccount(member, 1_000_000));
		Account receiver = saveAccount(saveMember());

		List<Integer> statuses = runConcurrently(24, i -> () -> {
			Account account = accounts.get(i % 4);
			ResultActions result = switch (i % 3) {
				case 0 -> postJson("/api/transactions/deposit", member, Map.of("accountId", account.getId(), "amount", 1_000));
				case 1 -> order("buy", member, account, stock, 10_000, 1);
				default -> transferCash(member, account, receiver, 100);
			};
			return result.andReturn().getResponse().getStatus();
		});

		assertThat(statuses).hasSize(24).containsOnly(200);
		accounts.add(receiver);
		List<String> entries = accounts.stream().flatMap(account -> entriesOf(account).stream()).toList();
		assertThat(entries).filteredOn(entry -> !entry.startsWith("TRANSFER_"))
				.hasSize(4 + 8 + 16).noneMatch(entry -> entry.endsWith("없음"));
		assertThat(totalOf(accounts)).isZero();
	}

	// 앞 거래가 같은 시스템 계정에 적고 커밋하지 않은 동안 뒤 거래가 줄을 서면 30초 안에 끝나지 못해 실패함
	@Test
	void 같은_시스템_계정에_적는_거래가_열려_있어도_다른_계좌의_거래는_기다리지_않는다() throws Exception {
		Account first = saveAccount(saveMember());
		Account second = saveAccount(saveMember());

		transactionTemplate.executeWithoutResult(status -> {
			transactionService.deposit(depositOf(first));
			try {
				runConcurrently(1, i -> () -> transactionService.deposit(depositOf(second)));
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});

		assertThat(entriesOf(first)).containsExactly("DEPOSIT 1000 -> EXTERNAL_CASH -1000");
		assertThat(entriesOf(second)).containsExactly("DEPOSIT 1000 -> EXTERNAL_CASH -1000");
	}

	@Test
	void 시스템_기록을_남기지_못하면_잔액과_거래_기록도_되돌린다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);

		jdbcTemplate.execute("rename table system_entry to system_entry_gone");
		try {
			postJson("/api/transactions/deposit", member, Map.of("accountId", account.getId(), "amount", 5_000))
					.andExpect(status().isInternalServerError());
		} finally {
			jdbcTemplate.execute("rename table system_entry_gone to system_entry");
		}

		assertThat(balanceOf(account)).isEqualTo(10_000);
		assertThat(entriesOf(account)).containsExactly("DEPOSIT 10000 -> EXTERNAL_CASH -10000");
	}

	private ResultActions order(String side, Member member, Account account, Stock stock, long price, int quantity) throws Exception {
		return postJson("/api/stocks/" + side, member, Map.of("accountId", account.getId(), "stockId", stock.getId(),
				"price", price, "quantity", quantity, "accountPassword", ACCOUNT_PASSWORD));
	}

	private ResultActions transferCash(Member member, Account from, Account to, long amount) throws Exception {
		return postJson("/api/transactions/" + from.getId() + "/transfer/" + to.getId(), member,
				Map.of("amount", amount, "accountPassword", ACCOUNT_PASSWORD));
	}

	private static DepositDto depositOf(Account account) {
		DepositDto deposit = new DepositDto();
		deposit.setAccountId(account.getId());
		deposit.setAmount(1_000L);
		return deposit;
	}

	private List<String> entriesOf(Account account) {
		return jdbcTemplate.queryForList("select concat(t.type, ' ', t.amount, ' -> ', coalesce(concat(e.system_account, ' ', e.amount), '없음'))"
				+ " from transaction t left join system_entry e on e.transaction_id = t.id"
				+ " where t.account_id = ? order by t.id", String.class, account.getId());
	}

	private long totalOf(List<Account> accounts) {
		return accounts.stream().mapToLong(account -> jdbcTemplate.queryForObject(
				"select sum(t.amount) + coalesce(sum(e.amount), 0)"
						+ " from transaction t left join system_entry e on e.transaction_id = t.id"
						+ " where t.account_id = ?", Long.class, account.getId())).sum();
	}
}
