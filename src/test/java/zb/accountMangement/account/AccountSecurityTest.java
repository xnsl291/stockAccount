package zb.accountMangement.account;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountSecurityTest extends ApiTest {

	@Test
	void 남의_계좌로는_입금_출금_송금_조회를_할_수_없다() throws Exception {
		Member owner = saveMember();
		Account ownerAccount = saveAccount(owner, 50_000);
		Member attacker = saveMember();
		Account attackerAccount = saveAccount(attacker);
		Long id = ownerAccount.getId();

		expectNotOwner(postJson("/api/transactions/deposit", attacker, Map.of("accountId", id, "amount", 1_000)));
		expectNotOwner(postJson("/api/transactions/withdrawal", attacker,
				Map.of("accountId", id, "amount", 1_000, "accountPassword", ACCOUNT_PASSWORD)));
		expectNotOwner(postJson("/api/transactions/" + id + "/transfer/" + attackerAccount.getId(), attacker,
				Map.of("amount", 1_000, "accountPassword", ACCOUNT_PASSWORD)));
		expectNotOwner(mockMvc.perform(as(attacker, get("/api/transactions/" + id + "/history"))));
		expectNotOwner(mockMvc.perform(as(attacker, get("/api/accounts/" + id))));

		assertThat(balanceOf(ownerAccount)).isEqualTo(50_000);
		assertThat(balanceOf(attackerAccount)).isZero();
	}

	@Test
	void 남의_계좌로는_주식_매매_이체_조회를_할_수_없다() throws Exception {
		Account ownerAccount = saveAccount(saveMember());
		Member attacker = saveMember();
		Account attackerAccount = saveAccount(attacker);
		Long id = ownerAccount.getId();
		Map<String, Object> trade = Map.of("accountId", id, "stockId", 1, "price", 1_000, "quantity", 1,
				"accountPassword", ACCOUNT_PASSWORD);

		expectNotOwner(postJson("/api/stocks/buy", attacker, trade));
		expectNotOwner(postJson("/api/stocks/sell", attacker, trade));
		expectNotOwner(postJson("/api/stocks/transfer", attacker, Map.of(
				"senderAccountId", id, "receiverAccountId", attackerAccount.getId(), "stockId", 1,
				"price", 1_000, "quantity", 1, "accountPassword", ACCOUNT_PASSWORD)));
		expectNotOwner(mockMvc.perform(as(attacker, get("/api/stocks/" + id + "/balance"))));
		expectNotOwner(mockMvc.perform(as(attacker, get("/api/stocks/" + id + "/history/?year=2024&month=1"))));
	}

	@Test
	void 계좌_비밀번호가_틀리면_출금과_송금이_되지_않는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		Account receiver = saveAccount(saveMember());

		withdraw(member, account, "0000")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		postJson("/api/transactions/" + account.getId() + "/transfer/" + receiver.getId(), member,
				Map.of("amount", 10_000, "accountPassword", "0000"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));

		assertThat(balanceOf(account)).isEqualTo(50_000);
		assertThat(balanceOf(receiver)).isZero();
	}

	@Test
	void 계좌_비밀번호를_연속_5회_틀리면_맞는_비밀번호로도_거래할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);

		for (int i = 0; i < 5; i++)
			withdraw(member, account, "0000").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));

		withdraw(member, account, ACCOUNT_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PENDING_ACCOUNT"));
		assertThat(balanceOf(account)).isEqualTo(50_000);
	}

	@Test
	void 비밀번호를_맞히면_틀린_횟수가_초기화된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);

		for (int i = 0; i < 4; i++)
			withdraw(member, account, "0000").andExpect(status().isBadRequest());
		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(status().isOk());
		for (int i = 0; i < 4; i++)
			withdraw(member, account, "0000").andExpect(status().isBadRequest());

		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(status().isOk());
		assertThat(balanceOf(account)).isEqualTo(30_000);
	}

	@Test
	void 계좌_한_건과_내_계좌_목록을_각각_조회할_수_있고_비밀번호는_응답에_없다() throws Exception {
		Member member = saveMember();
		Account first = saveAccount(member);
		Account second = saveAccount(member);
		saveAccount(saveMember());

		mockMvc.perform(as(member, get("/api/accounts/" + first.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(first.getId()))
				.andExpect(jsonPath("$.password").doesNotExist());
		mockMvc.perform(as(member, get("/api/accounts")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(first.getId()))
				.andExpect(jsonPath("$[1].id").value(second.getId()))
				.andExpect(jsonPath("$[0].password").doesNotExist());
	}

	private ResultActions withdraw(Member member, Account account, String password) throws Exception {
		return postJson("/api/transactions/withdrawal", member,
				Map.of("accountId", account.getId(), "amount", 10_000, "accountPassword", password));
	}

	private void expectNotOwner(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MISMATCH_ACCOUNT_OWNER"));
	}
}
