package zb.accountMangement.member;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.common.type.RoleType;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.service.MemberService;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static zb.accountMangement.member.model.VerificationPurpose.PHONE_CHANGE;
import static zb.accountMangement.member.model.VerificationPurpose.SIGN_UP;

class MemberSecurityTest extends ApiTest {

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	StockBalanceRepository stockBalanceRepository;

	@Autowired
	MemberService memberService;

	@Test
	void 인증번호를_5회_틀리면_맞는_인증번호로도_인증되지_않는다() throws Exception {
		String phone = randomPhone();
		String code = sendCode(phone);

		for (int i = 0; i < 5; i++)
			verifyPhone(phone, "x" + i, SIGN_UP).andExpect(jsonPath("$.code").value("UNMATCHED_VERIFICATION_CODE"));

		verifyPhone(phone, code, SIGN_UP)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VERIFICATION_CODE_NOT_EXIST"));
	}

	@Test
	void 인증번호를_다시_받으면_새_번호로_5번_시도할_수_있지만_발송은_10분에_3번까지다() throws Exception {
		String phone = randomPhone();
		for (int round = 0; round < 2; round++) {
			sendCode(phone);
			for (int i = 0; i < 5; i++)
				verifyPhone(phone, "x" + i, SIGN_UP).andExpect(jsonPath("$.code").value("UNMATCHED_VERIFICATION_CODE"));
		}

		String lastCode = sendCode(phone);
		for (int i = 0; i < 4; i++)
			verifyPhone(phone, "x" + i, SIGN_UP).andExpect(jsonPath("$.code").value("UNMATCHED_VERIFICATION_CODE"));
		verifyPhone(phone, lastCode, SIGN_UP).andExpect(status().isOk());

		sendCodeRequest(phone, randomIp())
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_SMS_REQUESTS"));
	}

	@Test
	void 같은_IP에서는_인증번호를_10분에_10번까지만_보내고_막힌_요청은_그_번호의_발송_횟수를_쓰지_않는다() throws Exception {
		String ip = randomIp();
		for (int i = 0; i < 10; i++)
			sendCodeRequest(randomPhone(), ip).andExpect(status().isOk());

		String victim = randomPhone();
		sendCodeRequest(victim, ip)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_SMS_REQUESTS"));
		verify(smsSender, times(0)).send(eq(victim), anyString());

		for (int i = 0; i < 3; i++)
			sendCodeRequest(victim, randomIp()).andExpect(status().isOk());
	}

	@Test
	void 같은_번호로는_인증번호를_10분에_3번까지만_보낸다() throws Exception {
		String phone = randomPhone();
		for (int i = 0; i < 3; i++)
			sendCode(phone);

		sendCodeRequest(phone, randomIp())
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_SMS_REQUESTS"));
		verify(smsSender, times(3)).send(eq(phone), anyString());
	}

	@Test
	void 인증번호를_4회_틀려도_다섯_번째에_맞히면_인증된다() throws Exception {
		String phone = randomPhone();
		String code = sendCode(phone);

		for (int i = 0; i < 4; i++)
			verifyPhone(phone, "x" + i, SIGN_UP).andExpect(status().isBadRequest());

		verifyPhone(phone, code, SIGN_UP).andExpect(status().isOk());
	}

	@Test
	void 새_번호를_인증하지_않으면_핸드폰번호를_바꿀_수_없다() throws Exception {
		Member member = saveMember();
		String oldPhone = member.getPhoneNumber();

		updatePhone(member, randomPhone())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNVERIFIED_PHONE_NUMBER"));

		assertThat(memberRepository.findById(member.getId()).orElseThrow().getPhoneNumber()).isEqualTo(oldPhone);
	}

	@Test
	void 새_번호를_인증하면_핸드폰번호를_바꿀_수_있다() throws Exception {
		Member member = saveMember();
		String newPhone = randomPhone();
		verifyPhone(newPhone, sendCode(newPhone), PHONE_CHANGE).andExpect(status().isOk());

		updatePhone(member, newPhone).andExpect(status().isOk());

		assertThat(memberRepository.findById(member.getId()).orElseThrow().getPhoneNumber()).isEqualTo(newPhone);
	}

	@Test
	void 같은_새_번호로_바꾸는_요청이_겹쳐도_500이_아니라_중복_번호_오류로_응답한다() throws Exception {
		Member member = saveMember();
		String oldPhone = member.getPhoneNumber();
		String newPhone = randomPhone();
		verifyPhone(newPhone, sendCode(newPhone), PHONE_CHANGE).andExpect(status().isOk());
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<MvcResult> update;

		// 다른 회원이 그 번호를 쓰도록 저장만 하고 커밋하지 않은 상태를 만들어, 번호 변경이 중복 검사는 통과하고 반영에서 기다리게 함
		TransactionStatus otherMember = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			memberRepository.saveAndFlush(Member.builder().name("other").phoneNumber(newPhone).password("x").build());
			update = executor.submit(() -> updatePhone(member, newPhone).andReturn());
			awaitLockWaits(1);
		} finally {
			transactionManager.commit(otherMember);
		}

		MvcResult result = update.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
				.isEqualTo("DUPLICATED_PHONE_NUMBER");
		assertThat(memberRepository.findById(member.getId()).orElseThrow().getPhoneNumber()).isEqualTo(oldPhone);
	}

	@Test
	void 가입용으로_받은_인증으로는_핸드폰번호를_바꿀_수_없다() throws Exception {
		Member member = saveMember();
		String newPhone = randomPhone();
		verifyPhone(newPhone, sendCode(newPhone), SIGN_UP).andExpect(status().isOk());

		updatePhone(member, newPhone).andExpect(jsonPath("$.code").value("UNVERIFIED_PHONE_NUMBER"));
	}

	@Test
	void 현재_비밀번호가_틀리면_회원정보를_바꿀_수_없다() throws Exception {
		Member member = saveMember();

		mockMvc.perform(as(member, patch("/api/member/" + member.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("name", member.getName(),
								"phoneNumber", member.getPhoneNumber(), "password", "stolen123", "currentPassword", "wrong"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));

		assertThat(passwordEncoder.matches("password123", memberRepository.findById(member.getId()).orElseThrow().getPassword())).isTrue();
	}

	@Test
	void 잔액이_남아_있으면_탈퇴할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 1_000);

		signOut(member)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REMAINING_ASSET"));

		assertThat(memberRepository.findById(member.getId()).orElseThrow().getRole()).isEqualTo(RoleType.USER);
		assertThat(statusOf(account)).isEqualTo(AccountStatus.EXISTS);
	}

	@Test
	void 보유_주식이_남아_있으면_탈퇴할_수_없다() throws Exception {
		Member member = saveMember();
		Account empty = saveAccount(member);
		Account holding = saveAccount(member);
		stockBalanceRepository.save(StockBalance.builder().accountId(holding.getId()).stockId(1L).quantity(1).build());

		signOut(member)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REMAINING_ASSET"));

		assertThat(statusOf(empty)).isEqualTo(AccountStatus.EXISTS);
		assertThat(statusOf(holding)).isEqualTo(AccountStatus.EXISTS);
	}

	@Test
	void 잔액과_보유_주식이_없으면_탈퇴하면서_계좌가_모두_해지된다() throws Exception {
		Member member = saveMember();
		Account first = saveAccount(member);
		Account second = saveAccount(member);

		signOut(member).andExpect(status().isOk());

		assertThat(memberRepository.findById(member.getId()).orElseThrow().getRole()).isEqualTo(RoleType.WITHDRAWN);
		assertThat(statusOf(first)).isEqualTo(AccountStatus.DELETED);
		assertThat(statusOf(second)).isEqualTo(AccountStatus.DELETED);
	}

	@Test
	void 탈퇴와_계좌_개설이_겹쳐도_탈퇴한_회원에게_살아_있는_계좌가_남지_않는다() throws Exception {
		Member member = saveMember();
		Account existing = saveAccount(member);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Future<Integer> signOut;
		Future<Integer> open;

		// 기존 계좌 행을 잠근 트랜잭션을 열어 두어, 탈퇴가 계좌 목록을 읽은 뒤 그 계좌의 잠금에서 기다리게 하고 그동안 계좌를 개설함
		TransactionStatus holdingAccount = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			accountRepository.findByIdForUpdate(existing.getId()).orElseThrow();
			signOut = executor.submit(() -> signOut(member).andReturn().getResponse().getStatus());
			awaitLockWaits(1);
			open = executor.submit(() -> mockMvc.perform(as(member, post("/api/accounts/open"))
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(Map.of("password", ACCOUNT_PASSWORD))))
					.andReturn().getResponse().getStatus());
			// 개설이 탈퇴보다 먼저 끝나거나, 탈퇴가 잡은 잠금에서 개설도 기다리기 시작할 때까지 진행시킨 뒤 계좌 잠금을 풂
			try {
				open.get(3, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				awaitLockWaits(2);
			}
		} finally {
			transactionManager.commit(holdingAccount);
		}

		assertThat(signOut.get(30, TimeUnit.SECONDS)).isEqualTo(200);
		open.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(memberRepository.findById(member.getId()).orElseThrow().getRole()).isEqualTo(RoleType.WITHDRAWN);
		assertThat(accountRepository.findAllById(accountRepository.findIdsByMemberId(member.getId()))).noneMatch(Account::isExistsAccount);
	}

	@Test
	void 회원정보_수정과_탈퇴가_겹쳐도_탈퇴가_되돌려지지_않는다() throws Exception {
		Member member = saveMember();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Integer> update;

		// 탈퇴가 회원 행에 하는 일(잠그고 탈퇴로 바꿈)을 커밋하지 않은 채로 회원정보 수정을 보내,
		// 수정이 회원을 읽은 뒤에 탈퇴가 커밋되는 순서를 만듦. 토큰 무효화는 Redis 쪽이라 이 겹침과 무관해 넣지 않음
		TransactionStatus signOut = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			memberService.getMemberForUpdate(member.getId()).delete();
			memberRepository.flush();
			update = executor.submit(() -> updatePhone(member, member.getPhoneNumber()).andReturn().getResponse().getStatus());
			awaitLockWaits(1);
		} finally {
			transactionManager.commit(signOut);
		}

		update.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(memberRepository.findById(member.getId()).orElseThrow().getRole()).isEqualTo(RoleType.WITHDRAWN);
	}

	private ResultActions updatePhone(Member member, String phone) throws Exception {
		return mockMvc.perform(as(member, patch("/api/member/" + member.getId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						Map.of("name", member.getName(), "phoneNumber", phone, "password", "password123", "currentPassword", "password123"))));
	}

	private ResultActions signOut(Member member) throws Exception {
		return mockMvc.perform(as(member, delete("/api/auth/sign-out")));
	}

	private AccountStatus statusOf(Account account) {
		return accountRepository.findById(account.getId()).orElseThrow().getStatus();
	}
}
