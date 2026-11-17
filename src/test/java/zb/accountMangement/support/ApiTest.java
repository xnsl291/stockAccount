package zb.accountMangement.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.repository.AccountRepository;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.repository.MemberRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인한 회원으로 API를 호출하는 테스트의 공통 부모 클래스
 */
public abstract class ApiTest extends IntegrationTest {

	protected static final String ACCOUNT_PASSWORD = "1234";

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected ObjectMapper objectMapper;
	@Autowired
	protected MemberRepository memberRepository;
	@Autowired
	protected AccountRepository accountRepository;
	@Autowired
	protected BCryptPasswordEncoder passwordEncoder;
	@Autowired
	protected JwtTokenProvider jwtTokenProvider;

	protected Member saveMember() {
		return memberRepository.save(Member.builder()
				.name("tester")
				.phoneNumber(randomPhone())
				.password(passwordEncoder.encode("password123"))
				.build());
	}

	protected Account saveAccount(Member owner) {
		return accountRepository.save(Account.builder()
				.memberId(owner.getId())
				.accountNumber(String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 100_000_000_000_000L)))
				.password(passwordEncoder.encode(ACCOUNT_PASSWORD))
				.build());
	}

	protected Account saveAccount(Member owner, long balance) throws Exception {
		Account account = saveAccount(owner);
		postJson("/api/transactions/deposit", owner, Map.of("accountId", account.getId(), "amount", balance))
				.andExpect(status().isOk());
		return account;
	}

	protected long balanceOf(Account account) {
		return accountRepository.findById(account.getId()).orElseThrow().getBalance();
	}

	protected ResultActions postJson(String url, Member loginMember, Map<String, Object> body) throws Exception {
		return postJson(url, loginMember, UUID.randomUUID().toString(), body);
	}

	protected ResultActions postJson(String url, Member loginMember, String idempotencyKey, Map<String, Object> body) throws Exception {
		return mockMvc.perform(as(loginMember, post(url))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	protected MockHttpServletRequestBuilder as(Member loginMember, MockHttpServletRequestBuilder request) {
		String accessToken = jwtTokenProvider.generateToken(loginMember.getId(), loginMember.getRole()).getAccessToken();
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
	}

	protected String randomPhone() {
		return "010" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
	}
}
