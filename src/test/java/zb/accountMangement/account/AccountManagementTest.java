package zb.accountMangement.account;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.common.type.RoleType;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static zb.accountMangement.member.model.VerificationPurpose.ACCOUNT_UNLOCK;
import static zb.accountMangement.member.model.VerificationPurpose.SIGN_UP;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountManagementTest extends ApiTest {

	@Test
	void 회원_조회와_수정_응답에_로그인_비밀번호_해시가_없다() throws Exception {
		Member member = saveMember();

		String found = mockMvc.perform(as(member, get("/api/member/" + member.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(member.getId()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String updated = mockMvc.perform(as(member, patch("/api/member/" + member.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"name", "renamed", "phoneNumber", member.getPhoneNumber(), "password", "newPassword1",
								"currentPassword", "password123"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("renamed"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		String newHash = memberRepository.findById(member.getId()).orElseThrow().getPassword();
		assertThat(found).doesNotContain(member.getPassword());
		assertThat(updated).doesNotContain(newHash);
	}

	@Test
	void 계좌_응답에_비밀번호와_틀린_횟수가_없다() throws Exception {
		Member member = saveMember();

		mockMvc.perform(as(member, post("/api/accounts/open"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("nickname", "생활비", "password", ACCOUNT_PASSWORD))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("생활비"))
				.andExpect(jsonPath("$.status").value("EXISTS"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordFailCount").doesNotExist());
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

		assertThat(accountRepository.findByMemberId(member.getId())).isEmpty();
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

		assertThat(accountRepository.findByMemberId(member.getId())).isEmpty();
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
		withdraw(member, account, "5678").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));

		updateAccount(member, account, ACCOUNT_PASSWORD, "5678")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.password").doesNotExist());
		withdraw(member, account, ACCOUNT_PASSWORD).andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));
		withdraw(member, account, "5678").andExpect(status().isOk());
		assertThat(balanceOf(account)).isEqualTo(40_000);
	}

	@Test
	void 계좌_수정에서_현재_비밀번호를_연속_5회_틀려도_거래정지된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);

		for (int i = 0; i < 5; i++)
			updateAccount(member, account, "0000", "5678").andExpect(jsonPath("$.code").value("UNMATCHED_PASSWORD"));

		withdraw(member, account, ACCOUNT_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PENDING_ACCOUNT"));
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
		// 두 요청이 모두 인증 표시를 읽은 뒤에야 지우러 가게 해, 읽기와 지우기 사이에 다른 요청이 끼어드는 순서를 매번 만듦.
		// 그냥 동시에 보내면 한 요청이 지운 뒤에 다른 요청이 읽어 우연히 하나만 풀릴 수 있음
		CyclicBarrier bothRead = new CyclicBarrier(2);
		doAnswer(invocation -> {
			Object value = invocation.callRealMethod();
			bothRead.await(10, TimeUnit.SECONDS);
			return value;
		}).when(redisService).getData(argThat(key -> key.startsWith("SMS_VERIFIED:")));

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
