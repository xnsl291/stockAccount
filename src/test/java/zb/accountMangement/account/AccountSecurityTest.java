package zb.accountMangement.account;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
	void 틀린_비밀번호가_동시에_몰려도_틀림_응답은_5건뿐이고_나머지는_거래정지로_거절된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		int requests = 30;
		// 모든 요청이 비밀번호 비교를 마친 뒤에 다음으로 가게 해, 모두가 "아직 정지 전"을 본 상태에서 틀린 횟수를 올리러 가는 순서를 만듦.
		// 비교를 계좌 잠금 안에서 하는 구조라면 한 번에 한 요청만 비교하므로 2초 뒤 기다림을 풀고 그대로 진행함
		CyclicBarrier allCompared = new CyclicBarrier(requests);
		doAnswer(invocation -> {
			Object matched = invocation.callRealMethod();
			try {
				allCompared.await(2, TimeUnit.SECONDS);
			} catch (TimeoutException | BrokenBarrierException e) {
				// 다 모이지 못하면 기다리지 않고 진행함
			}
			return matched;
		}).when(passwordEncoder).matches(any(), any());

		List<String> codes = runConcurrently(requests, i -> () -> errorCode(withdraw(member, account, "0000")));

		assertThat(codes).filteredOn("UNMATCHED_PASSWORD"::equals).hasSize(5);
		assertThat(codes).filteredOn("PENDING_ACCOUNT"::equals).hasSize(requests - 5);
		assertThat(passwordFailCountOf(account)).isEqualTo(5);
		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(jsonPath("$.code").value("PENDING_ACCOUNT"));
		assertThat(balanceOf(account)).isEqualTo(50_000);
	}

	@Test
	void 비밀번호_변경이_먼저_커밋되면_그전에_읽어_둔_옛_비밀번호로는_거래할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		CountDownLatch compared = new CountDownLatch(1);
		CountDownLatch changed = new CountDownLatch(1);
		AtomicBoolean first = new AtomicBoolean(true);
		// 옛 비밀번호 요청이 옛 해시와 비교를 마친 직후 멈춰 있는 동안 비밀번호 변경을 커밋함
		doAnswer(invocation -> {
			Object matched = invocation.callRealMethod();
			if (first.compareAndSet(true, false)) {
				compared.countDown();
				changed.await(10, TimeUnit.SECONDS);
			}
			return matched;
		}).when(passwordEncoder).matches(any(), any());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<MvcResult> oldPasswordRequest = executor.submit(() -> withdraw(member, account, ACCOUNT_PASSWORD).andReturn());
			assertThat(compared.await(10, TimeUnit.SECONDS)).isTrue();
			// 비교를 계좌 잠금 안에서 하는 구조라면 변경이 그 잠금에 막혀 여기서 시간 초과로 실패함
			executor.submit(() -> changeAccountPassword(account, "5678")).get(5, TimeUnit.SECONDS);
			changed.countDown();

			MvcResult result = oldPasswordRequest.get(10, TimeUnit.SECONDS);
			assertThat(result.getResponse().getStatus()).isEqualTo(400);
			assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asText())
					.isEqualTo("UNMATCHED_PASSWORD");
		} finally {
			changed.countDown();
			executor.shutdown();
			executor.awaitTermination(15, TimeUnit.SECONDS);
		}
		assertThat(balanceOf(account)).isEqualTo(50_000);
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

	private String errorCode(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("code").asText();
	}

	private void expectNotOwner(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MISMATCH_ACCOUNT_OWNER"));
	}
}
