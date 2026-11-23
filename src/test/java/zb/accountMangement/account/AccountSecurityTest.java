package zb.accountMangement.account;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
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

	@Autowired
	PlatformTransactionManager transactionManager;

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
		// 다 모이지 못하면 기다림을 풀고 진행하되, 의도한 순서를 만들지 못한 것이므로 아래에서 실패로 처리함.
		// 비교를 계좌 잠금 안에서 하는 구조라면 한 번에 한 요청만 비교하므로 여기서 실패함
		CyclicBarrier allCompared = new CyclicBarrier(requests);
		doAnswer(invocation -> {
			Object matched = invocation.callRealMethod();
			try {
				allCompared.await(10, TimeUnit.SECONDS);
			} catch (TimeoutException | BrokenBarrierException e) {
				// 장벽이 깨진 채로 남아 아래 단언이 잡음
			}
			return matched;
		}).when(passwordEncoder).matches(any(), any());

		List<String> codes = runConcurrently(requests, i -> () -> errorCode(withdraw(member, account, "0000")));

		assertThat(allCompared.isBroken()).as("모든 요청이 비교를 마친 자리에 모이지 못함").isFalse();

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
	void 틀렸다고_비교한_뒤에_비밀번호가_바뀌어_새_비밀번호와_맞으면_틀린_횟수로_세지_않고_거래한다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		for (int i = 0; i < 2; i++)
			withdraw(member, account, "0000").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		CountDownLatch compared = new CountDownLatch(1);
		CountDownLatch changed = new CountDownLatch(1);
		AtomicBoolean first = new AtomicBoolean(true);
		// 새 비밀번호 요청이 옛 해시와 비교해 틀린 직후 멈춰 있는 동안 비밀번호 변경을 커밋함
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
			Future<MvcResult> newPasswordRequest = executor.submit(() -> withdraw(member, account, "5678").andReturn());
			assertThat(compared.await(10, TimeUnit.SECONDS)).isTrue();
			executor.submit(() -> changeAccountPassword(account, "5678")).get(5, TimeUnit.SECONDS);
			changed.countDown();

			assertThat(newPasswordRequest.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
		} finally {
			changed.countDown();
			executor.shutdown();
			executor.awaitTermination(15, TimeUnit.SECONDS);
		}
		assertThat(passwordFailCountOf(account)).isZero();
		assertThat(balanceOf(account)).isEqualTo(40_000);
	}

	@Test
	void 맞는_비밀번호를_확인한_뒤_실행_전에_계좌가_거래정지되면_틀린_요청과_같은_거래정지_응답을_받는다() throws Exception {
		Member member = saveMember();
		Account receiver = saveAccount(saveMember());

		Account withdrawFrom = saveAccount(member, 50_000);
		expectSuspendedAfterPasswordCheck(withdrawFrom, () -> withdraw(member, withdrawFrom, ACCOUNT_PASSWORD).andReturn());
		Account transferFrom = saveAccount(member, 50_000);
		expectSuspendedAfterPasswordCheck(transferFrom, () -> postJson("/api/transactions/" + transferFrom.getId() + "/transfer/" + receiver.getId(),
				member, Map.of("amount", 10_000, "accountPassword", ACCOUNT_PASSWORD)).andReturn());
		Account buyFrom = saveAccount(member, 50_000);
		expectSuspendedAfterPasswordCheck(buyFrom, () -> postJson("/api/stocks/buy", member, Map.of("accountId", buyFrom.getId(),
				"stockId", saveStock(1_000).getId(), "price", 1_000, "quantity", 1, "accountPassword", ACCOUNT_PASSWORD)).andReturn());
		Account stockFrom = saveAccount(member, 50_000);
		expectSuspendedAfterPasswordCheck(stockFrom, () -> postJson("/api/stocks/transfer", member, Map.of("senderAccountId", stockFrom.getId(),
				"receiverAccountId", receiver.getId(), "stockId", saveStock(1_000).getId(), "quantity", 1,
				"accountPassword", ACCOUNT_PASSWORD)).andReturn());

		assertThat(balanceOf(receiver)).isZero();
	}

	// 계좌 행을 잠가 둔 채 맞는 비밀번호 요청을 보내, 비밀번호 확인은 마치고 실행 단계의 계좌 잠금에서 기다리게 함.
	// 그동안 틀린 요청 다섯 건이 커밋된 것처럼 계좌를 거래정지시키고 잠금을 풂.
	// 정답 요청만 다른 오류 코드를 받으면, 동시에 여러 비밀번호를 넣어 본 쪽이 어느 것이 맞았는지 알 수 있음
	private void expectSuspendedAfterPasswordCheck(Account account, Callable<MvcResult> correctRequest) throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<MvcResult> request;
		TransactionStatus wrongAttempts = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			Account locked = accountRepository.findByIdForUpdate(account.getId()).orElseThrow();
			request = executor.submit(correctRequest);
			awaitLockWaits(1);
			for (int i = 0; i < 5; i++)
				locked.recordPasswordFailure();
		} catch (Exception | AssertionError e) {
			transactionManager.rollback(wrongAttempts);
			executor.shutdownNow();
			throw e;
		}
		transactionManager.commit(wrongAttempts);

		MvcResult result = request.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asText())
				.isEqualTo("PENDING_ACCOUNT");
		assertThat(balanceOf(account)).isEqualTo(50_000);
	}

	@Test
	void 틀린_횟수가_남은_계좌에서_맞는_요청의_확정과_틀린_요청이_겹치면_줄_세운_순서대로_횟수가_남는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		for (int i = 0; i < 3; i++)
			withdraw(member, account, "0000").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<MvcResult> correct;

		// 계좌 행을 잠가 둔 채 맞는 요청을 보내, 틀린 횟수 3을 읽고 0으로 되돌리려는 자리에서 기다리게 함.
		// 그동안 틀린 요청 한 건이 커밋된 것처럼 횟수를 4로 올리고 잠금을 풂.
		// 틀린 요청이 먼저 처리되고 맞는 요청이 뒤따른 순서와 같아야 하므로 횟수는 0이어야 함
		TransactionStatus wrongAttempt = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			Account locked = accountRepository.findByIdForUpdate(account.getId()).orElseThrow();
			correct = executor.submit(() -> withdraw(member, account, ACCOUNT_PASSWORD).andReturn());
			awaitLockWaits(1);
			locked.recordPasswordFailure();
		} catch (Exception | AssertionError e) {
			transactionManager.rollback(wrongAttempt);
			executor.shutdownNow();
			throw e;
		}
		transactionManager.commit(wrongAttempt);

		MvcResult result = correct.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(passwordFailCountOf(account)).isZero();
		assertThat(balanceOf(account)).isEqualTo(40_000);
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
