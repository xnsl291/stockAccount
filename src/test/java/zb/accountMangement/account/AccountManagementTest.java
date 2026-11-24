package zb.accountMangement.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import zb.accountMangement.account.dto.AccountManagementDto;
import zb.accountMangement.account.dto.UpdateAccountDto;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.account.model.TransactionCause;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.common.type.RoleType;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static zb.accountMangement.member.model.VerificationPurpose.ACCOUNT_UNLOCK;
import static zb.accountMangement.member.model.VerificationPurpose.SIGN_UP;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountManagementTest extends ApiTest {

	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	StockBalanceRepository stockBalanceRepository;
	@Autowired
	AccountService accountService;
	@Autowired
	TransactionService transactionService;

	@Test
	void 계좌를_개설하고_한_건과_내_계좌_목록을_조회할_수_있고_응답에_비밀번호와_틀린_횟수가_없다() throws Exception {
		Member member = saveMember();
		Account first = saveAccount(member);
		saveAccount(saveMember());

		String opened = mockMvc.perform(as(member, post("/api/accounts/open"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("nickname", "생활비", "password", ACCOUNT_PASSWORD))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("생활비"))
				.andExpect(jsonPath("$.status").value("EXISTS"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordFailCount").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		long openedId = objectMapper.readTree(opened).get("id").asLong();

		mockMvc.perform(as(member, get("/api/accounts/" + first.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(first.getId()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordFailCount").doesNotExist());
		mockMvc.perform(as(member, get("/api/accounts")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(first.getId()))
				.andExpect(jsonPath("$[1].id").value(openedId))
				.andExpect(jsonPath("$[0].password").doesNotExist());
	}

	@Test
	void 계좌_비밀번호가_없으면_계좌를_개설할_수_없다() throws Exception {
		Member member = saveMember();

		mockMvc.perform(as(member, post("/api/accounts/open"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("nickname", "생활비"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
				.andExpect(jsonPath("$.errors.password").exists());

		assertThat(accountRepository.findIdsByMemberId(member.getId())).isEmpty();
	}

	@Test
	void 별명이_DB_칸보다_길면_계좌번호_중복이_아니라_입력_검증_오류로_응답한다() throws Exception {
		Member member = saveMember();

		mockMvc.perform(as(member, post("/api/accounts/open"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("nickname", "가".repeat(256), "password", ACCOUNT_PASSWORD))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
				.andExpect(jsonPath("$.errors.nickname").exists());

		assertThat(accountRepository.findIdsByMemberId(member.getId())).isEmpty();
	}

	@Test
	void 계좌번호_유니크_위반이_아닌_저장_오류는_계좌번호_중복으로_바꾸지_않는다() {
		Member member = saveMember();
		// 입력 검증을 거치지 않는 서비스 호출로, 길이 초과처럼 유니크 위반이 아닌 저장 오류를 일부러 냄
		AccountManagementDto tooLong = AccountManagementDto.builder().nickname("가".repeat(256)).password(ACCOUNT_PASSWORD).build();

		assertThatThrownBy(() -> accountService.openAccount(member.getId(), tooLong))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 탈퇴한_회원은_남은_액세스_토큰으로도_계좌를_개설할_수_없다() throws Exception {
		Member member = saveMember();
		MockHttpServletRequestBuilder openWithTokenIssuedBeforeWithdrawal = as(member, post("/api/accounts/open"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("password", ACCOUNT_PASSWORD)));
		member.setRole(RoleType.WITHDRAWN);
		memberRepository.save(member);

		mockMvc.perform(openWithTokenIssuedBeforeWithdrawal)
				.andExpect(jsonPath("$.code").value("WITHDRAWN_MEMBER"));

		assertThat(accountRepository.findIdsByMemberId(member.getId())).isEmpty();
	}

	@Test
	void 같은_계좌번호의_개설이_겹쳐도_500이_아니라_계좌번호_중복_오류로_응답한다() throws Exception {
		Member member = saveMember();
		Member other = saveMember();
		String accountNumber = "9" + ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 10_000_000_000_000L);
		doReturn(accountNumber).when(accountNumberGenerator).generate();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<MvcResult> open;

		// 같은 번호의 계좌를 저장만 하고 커밋하지 않은 상태를 만들어, 개설 요청이 번호 조회는 통과하고 저장에서 기다리게 함
		TransactionStatus otherOpen = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			accountRepository.saveAndFlush(Account.builder()
					.memberId(other.getId()).accountNumber(accountNumber).password("x").build());
			open = executor.submit(() -> mockMvc.perform(as(member, post("/api/accounts/open"))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(Map.of("password", ACCOUNT_PASSWORD))))
					.andReturn());
			awaitLockWaits(1);
		} finally {
			transactionManager.commit(otherOpen);
		}

		MvcResult result = open.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(result.getResponse().getStatus()).isEqualTo(409);
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
				.isEqualTo("DUPLICATED_ACCOUNT_NUMBER");
		assertThat(accountRepository.findIdsByMemberId(member.getId())).isEmpty();
	}

	@Test
	void 새_계좌_비밀번호는_빈_값으로_바꿀_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);

		updateAccount(member, account, ACCOUNT_PASSWORD, " ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.newPassword").exists());
		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 계좌_비밀번호를_바꾸려면_현재_비밀번호가_맞아야_한다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);

		updateAccount(member, account, "0000", "5678")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		// 출금과 같은 틀린 횟수에 더해짐. 한도에 이르면 거래정지되는 것은 계좌 보안 테스트가 확인함
		assertThat(passwordFailCountOf(account)).isEqualTo(1);
		withdraw(member, account, "5678").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));

		updateAccount(member, account, ACCOUNT_PASSWORD, "5678")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.password").doesNotExist());
		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		withdraw(member, account, "5678").andExpect(status().isOk());
		assertThat(balanceOf(account)).isEqualTo(40_000);
	}

	@Test
	void 거래정지된_계좌는_등록된_번호로_문자_인증을_마쳐야_풀리고_다시_거래할_수_있다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		lock(member, account);

		unlock(member, account)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNVERIFIED_PHONE_NUMBER"));
		verifyPhone(member.getPhoneNumber(), sendCode(member.getPhoneNumber()), SIGN_UP).andExpect(status().isOk());
		unlock(member, account).andExpect(jsonPath("$.code").value("UNVERIFIED_PHONE_NUMBER"));
		String otherPhone = randomPhone();
		verifyPhone(otherPhone, sendCode(otherPhone), ACCOUNT_UNLOCK).andExpect(status().isOk());
		unlock(member, account).andExpect(jsonPath("$.code").value("UNVERIFIED_PHONE_NUMBER"));
		assertThat(statusOf(account)).isEqualTo(AccountStatus.PENDING);

		verifyPhone(member.getPhoneNumber(), sendCode(member.getPhoneNumber()), ACCOUNT_UNLOCK).andExpect(status().isOk());
		unlock(member, account)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EXISTS"));

		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(status().isOk());
		assertThat(balanceOf(account)).isEqualTo(40_000);
		// 틀린 횟수도 0으로 돌아가 한 번 틀렸다고 다시 정지되지 않음
		withdraw(member, account, "0000").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 문자_인증_한_번으로는_거래정지된_계좌_하나만_풀린다() throws Exception {
		Member member = saveMember();
		Account first = saveAccount(member, 50_000);
		Account second = saveAccount(member, 50_000);
		lock(member, first);
		lock(member, second);
		verifyPhone(member.getPhoneNumber(), sendCode(member.getPhoneNumber()), ACCOUNT_UNLOCK).andExpect(status().isOk());
		// 두 요청이 모두 인증 표시에 처음 손대기 직전까지 온 뒤에 함께 출발시킴. 그냥 동시에 보내면 한 요청이 끝난 뒤에
		// 다른 요청이 시작돼 우연히 하나만 풀릴 수 있음. 읽은 뒤 지우는 방식이면 둘 다 읽고 나서 지워 둘 다 풀려 실패함
		CyclicBarrier bothArrived = new CyclicBarrier(2);
		Set<Long> arrived = ConcurrentHashMap.newKeySet();
		Answer<Object> waitForOtherOnce = invocation -> {
			if (arrived.add(Thread.currentThread().getId()))
				bothArrived.await(10, TimeUnit.SECONDS);
			return invocation.callRealMethod();
		};
		doAnswer(waitForOtherOnce).when(redisService).getData(argThat(key -> key.startsWith("SMS_VERIFIED:")));
		doAnswer(waitForOtherOnce).when(redisService).deleteIfEquals(argThat(key -> key.startsWith("SMS_VERIFIED:")), anyString());

		List<Integer> statuses = runConcurrently(2, i -> () ->
				unlock(member, i == 0 ? first : second).andReturn().getResponse().getStatus());

		assertThat(statuses).containsExactlyInAnyOrder(200, 400);
		assertThat(List.of(statusOf(first), statusOf(second)))
				.containsExactlyInAnyOrder(AccountStatus.EXISTS, AccountStatus.PENDING);
	}

	@Test
	void 남의_계좌는_잠금을_풀_수_없다() throws Exception {
		Member owner = saveMember();
		Account account = saveAccount(owner, 50_000);
		lock(owner, account);
		Member attacker = saveMember();
		verifyPhone(attacker.getPhoneNumber(), sendCode(attacker.getPhoneNumber()), ACCOUNT_UNLOCK).andExpect(status().isOk());

		unlock(attacker, account)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MISMATCH_ACCOUNT_OWNER"));
		assertThat(statusOf(account)).isEqualTo(AccountStatus.PENDING);
	}

	@Test
	void 계좌번호로_검색하면_내_계좌는_잔액까지_남의_계좌는_소유주만_보인다() throws Exception {
		Member member = saveMember();
		Account mine = saveAccount(member, 10_000);
		Member other = saveMember();
		Account others = saveAccount(other, 20_000);

		search(member, mine.getAccountNumber())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(mine.getId()))
				.andExpect(jsonPath("$.balance").value(10_000));
		search(member, others.getAccountNumber())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(others.getId()))
				.andExpect(jsonPath("$.ownerName").value(other.getName()))
				.andExpect(jsonPath("$.balance").doesNotExist());
		search(member, String.valueOf(others.getId()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_EXIST"));
	}

	@Test
	void 해지한_계좌는_계좌번호로_검색되지_않는다() throws Exception {
		Member member = saveMember();
		Account closed = saveAccount(member);
		saveAccount(member);
		mockMvc.perform(as(member, delete("/api/accounts/" + closed.getId()))).andExpect(status().isOk());

		search(saveMember(), closed.getAccountNumber())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_EXIST"));
	}

	@Test
	void 해지한_계좌를_빼고_하나만_남으면_해지를_거절하고_계좌_목록에는_해지한_계좌가_없다() throws Exception {
		Member member = saveMember();
		Account closed = saveAccount(member);
		Account last = saveAccount(member);
		closeAccount(member, closed).andExpect(status().isOk());

		closeAccount(member, last)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("LAST_ACCOUNT"));

		assertThat(statusOf(last)).isEqualTo(AccountStatus.EXISTS);
		mockMvc.perform(as(member, get("/api/accounts")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(last.getId()));
	}

	@Test
	void 계좌_두_개를_동시에_해지해도_하나는_남는다() throws Exception {
		Member member = saveMember();
		Account first = saveAccount(member);
		Account second = saveAccount(member);
		Stock stock = saveStock(1_000);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<Integer>> closes = new ArrayList<>();

		// 두 계좌에 보유 주식을 넣고 커밋하지 않은 상태를 만들어, 두 해지 요청이 계좌 수를 센 다음 보유 주식 확인에서 기다리게 함.
		// 그 뒤 넣은 것을 되돌려 두 요청이 해지를 이어 가게 함. 계좌 수를 잠금 없이 세면 둘 다 "2개"를 본 채로 해지함
		TransactionStatus incoming = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			for (Account account : List.of(first, second))
				stockBalanceRepository.saveAndFlush(StockBalance.builder()
						.accountId(account.getId()).stockId(stock.getId()).quantity(1).build());
			for (Account account : List.of(first, second))
				closes.add(executor.submit(() -> closeAccount(member, account).andReturn().getResponse().getStatus()));
			awaitLockWaits(2);
		} finally {
			transactionManager.rollback(incoming);
		}

		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> close : closes)
			statuses.add(close.get(30, TimeUnit.SECONDS));
		executor.shutdown();
		assertThat(statuses).containsExactlyInAnyOrder(200, 400);
		assertThat(List.of(statusOf(first), statusOf(second)))
				.containsExactlyInAnyOrder(AccountStatus.EXISTS, AccountStatus.DELETED);
	}

	@Test
	void 별명_수정과_입금이_겹쳐도_입금이_사라지지_않는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		UpdateAccountDto rename = new UpdateAccountDto();
		rename.setNickname("생활비");
		rename.setCurrentPassword(ACCOUNT_PASSWORD);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Account> update;

		// 입금을 반영하고 커밋하지 않은 채로 별명 수정을 돌려, 수정이 계좌를 읽은 뒤에 입금이 커밋되는 순서를 만듦.
		// 계좌 비밀번호 확인은 수정보다 앞서 끝나는 별도 트랜잭션이라 이 겹침과 무관해 서비스를 바로 부름
		TransactionStatus deposit = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			transactionService.changeBalance(accountService.getAccountForUpdate(account.getId()), TransactionType.DEPOSIT, 10_000, "입금", null,
					TransactionCause.NONE);
			update = executor.submit(() -> accountService.updateAccount(account.getId(), rename));
			awaitLockWaits(1);
		} finally {
			transactionManager.commit(deposit);
		}

		update.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		Account saved = accountRepository.findById(account.getId()).orElseThrow();
		assertThat(saved.getNickname()).isEqualTo("생활비");
		assertThat(saved.getBalance()).isEqualTo(60_000);
	}

	private ResultActions closeAccount(Member member, Account account) throws Exception {
		return mockMvc.perform(as(member, delete("/api/accounts/" + account.getId())));
	}

	private void lock(Member member, Account account) throws Exception {
		for (int i = 0; i < 5; i++)
			withdraw(member, account, "0000").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		assertThat(statusOf(account)).isEqualTo(AccountStatus.PENDING);
	}

	private ResultActions unlock(Member member, Account account) throws Exception {
		return mockMvc.perform(as(member, post("/api/accounts/" + account.getId() + "/unlock")));
	}

	private ResultActions updateAccount(Member member, Account account, String currentPassword, String newPassword) throws Exception {
		return mockMvc.perform(as(member, patch("/api/accounts/" + account.getId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
	}

	private ResultActions withdraw(Member member, Account account, String password) throws Exception {
		return postJson("/api/transactions/withdrawal", member,
				Map.of("accountId", account.getId(), "amount", 10_000, "accountPassword", password));
	}

	private ResultActions search(Member member, String accountNumber) throws Exception {
		return mockMvc.perform(as(member, get("/api/accounts/search/" + accountNumber)));
	}

	private AccountStatus statusOf(Account account) {
		return accountRepository.findById(account.getId()).orElseThrow().getStatus();
	}
}
