package zb.accountMangement.account;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.support.ApiTest;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionHistoryTest extends ApiTest {

	@Test
	void 거래내역은_최근_거래부터_페이지_단위로_나뉜다() throws Exception {
		Member member = saveMember();
		Account account = fiveTransactions(member);

		history(member, account, "?size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3))
				.andExpect(jsonPath("$.content[*].balanceAfter", contains(16_000, 12_000)));
		history(member, account, "?size=2&page=1")
				.andExpect(jsonPath("$.content[*].balanceAfter", contains(14_000, 15_000)));
		history(member, account, "?size=2&page=2")
				.andExpect(jsonPath("$.content[*].balanceAfter", contains(10_000)));
	}

	@Test
	void 거래_종류와_기간으로_걸러_볼_수_있다() throws Exception {
		Member member = saveMember();
		// 거래 도중 자정이 지나도 맞도록 거래 전후의 날짜로 범위를 잡음
		LocalDate before = LocalDate.now();
		Account account = fiveTransactions(member);
		LocalDate after = LocalDate.now();

		history(member, account, "?type=WITHDRAW")
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[*].amount", contains(-2_000, -1_000)));
		history(member, account, "?from=" + before + "&to=" + after)
				.andExpect(jsonPath("$.totalElements").value(5));
		history(member, account, "?from=" + after.plusDays(1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		history(member, account, "?to=" + before.minusDays(1))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void 잘못된_조회_조건은_400() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member);

		LocalDate today = LocalDate.now();
		history(member, account, "?from=" + today + "&to=" + today.minusDays(1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST_DATE"));
		for (String query : new String[] {"?size=101", "?page=-1", "?type=NOT_A_TYPE", "?from=2026/01/01"})
			history(member, account, query)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
	}

	/**
	 * 잔액 흐름 10,000 → 15,000 → 14,000 → 12,000 → 16,000
	 */
	private Account fiveTransactions(Member member) throws Exception {
		Account account = saveAccount(member, 10_000);
		postJson("/api/transactions/deposit", member, Map.of("accountId", account.getId(), "amount", 5_000))
				.andExpect(status().isOk());
		withdraw(member, account, 1_000);
		withdraw(member, account, 2_000);
		postJson("/api/transactions/deposit", member, Map.of("accountId", account.getId(), "amount", 4_000))
				.andExpect(status().isOk());
		return account;
	}

	private void withdraw(Member member, Account account, long amount) throws Exception {
		postJson("/api/transactions/withdrawal", member,
				Map.of("accountId", account.getId(), "amount", amount, "accountPassword", ACCOUNT_PASSWORD))
				.andExpect(status().isOk());
	}

	private ResultActions history(Member member, Account account, String query) throws Exception {
		return mockMvc.perform(as(member, get("/api/transactions/" + account.getId() + "/history" + query)));
	}
}
