package zb.accountMangement.member;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SignUpTest extends ApiTest {

	private static final String PASSWORD = "password123";
	private static final Pattern CODE = Pattern.compile("\\[(\\d+)]");

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void 문자_인증_후_가입하면_로그인해서_인증이_필요한_API를_호출할_수_있다() throws Exception {
		String phone = randomPhone();
		verifyPhone(phone);

		signUp(phone).andExpect(status().isOk());

		Member member = memberRepository.findByPhoneNumber(phone).orElseThrow();
		assertThat(member.getPassword()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, member.getPassword())).isTrue();

		List<Account> accounts = accountRepository.findByMemberIdAndStatusNot(member.getId(), AccountStatus.DELETED);
		assertThat(accounts).hasSize(1);
		assertThat(passwordEncoder.matches(ACCOUNT_PASSWORD, accounts.get(0).getPassword())).isTrue();

		mockMvc.perform(get("/api/member/" + member.getId()).header(HttpHeaders.AUTHORIZATION, bearer(login(phone, PASSWORD))))
				.andExpect(status().isOk());
	}

	@Test
	void 하이픈이_섞인_번호로도_인증과_가입이_같은_번호로_처리된다() throws Exception {
		String phone = randomPhone();
		String hyphenated = phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
		verifyPhone(hyphenated);

		signUp(phone).andExpect(status().isOk());
	}

	@Test
	void 핸드폰_인증을_하지_않으면_가입할_수_없다() throws Exception {
		signUp(randomPhone())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNVERIFIED_PHONE_NUMBER"));
	}

	@Test
	void 이미_가입한_번호로는_가입할_수_없다() throws Exception {
		String phone = randomPhone();
		verifyPhone(phone);
		signUp(phone).andExpect(status().isOk());

		verifyPhone(phone);
		signUp(phone)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DUPLICATED_PHONE_NUMBER"));
	}

	@Test
	void 같은_번호의_가입이_겹쳐도_500이_아니라_중복_번호_오류로_응답한다() throws Exception {
		String phone = randomPhone();
		verifyPhone(phone);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<MvcResult> signUp;

		// 같은 번호의 회원을 저장만 하고 커밋하지 않은 상태를 만들어, 가입 요청이 중복 검사는 통과하고 저장에서 기다리게 함
		TransactionStatus otherSignUp = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			memberRepository.saveAndFlush(Member.builder().name("tester").phoneNumber(phone).password("x").build());
			signUp = executor.submit(() -> signUp(phone).andReturn());
			awaitLockWaits(1);
		} finally {
			transactionManager.commit(otherSignUp);
		}

		MvcResult result = signUp.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
				.isEqualTo("DUPLICATED_PHONE_NUMBER");
	}

	@Test
	void 탈퇴하면_로그인할_수_없고_기존_토큰도_거부된다() throws Exception {
		String phone = signedUpMember();
		String accessToken = login(phone, PASSWORD);
		Long memberId = memberRepository.findByPhoneNumber(phone).orElseThrow().getId();

		mockMvc.perform(delete("/api/auth/sign-out").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
				.andExpect(status().isOk());

		postJson("/api/auth/login", Map.of("phoneNumber", phone, "password", PASSWORD))
				.andExpect(jsonPath("$.code").value("WITHDRAWN_MEMBER"));
		mockMvc.perform(get("/api/member/" + memberId).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 회원정보로_비밀번호를_바꾸면_암호화되어_저장되고_이전_리프레시_토큰은_쓸_수_없다() throws Exception {
		String phone = signedUpMember();
		JsonNode tokens = loginTokens(phone, PASSWORD);
		Member member = memberRepository.findByPhoneNumber(phone).orElseThrow();

		mockMvc.perform(patch("/api/member/" + member.getId())
						.header(HttpHeaders.AUTHORIZATION, bearer(tokens.get("accessToken").asText()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("name", "newname", "phoneNumber", phone,
								"password", "newpassword", "currentPassword", PASSWORD))))
				.andExpect(status().isOk());

		assertThat(memberRepository.findById(member.getId()).orElseThrow().getPassword()).isNotEqualTo("newpassword");
		reissue(tokens.get("refreshToken").asText()).andExpect(status().isUnauthorized());
		login(phone, "newpassword");
	}

	@Test
	void 문자_인증으로_비밀번호를_재설정하면_새_비밀번호로_로그인되고_이전_리프레시_토큰은_쓸_수_없다() throws Exception {
		String phone = signedUpMember();
		JsonNode tokens = loginTokens(phone, PASSWORD);
		String accessToken = tokens.get("accessToken").asText();
		Member member = memberRepository.findByPhoneNumber(phone).orElseThrow();

		mockMvc.perform(post("/api/member/find-pw/" + member.getId())
						.with(fromIp(randomIp()))
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("name", member.getName(), "phoneNumber", phone))))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/member/find-pw/" + member.getId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("inputCode", lastCodeSentTo(phone), "newPassword", "resetpassword"))))
				.andExpect(status().isOk());

		reissue(tokens.get("refreshToken").asText()).andExpect(status().isUnauthorized());
		login(phone, "resetpassword");
	}

	private String signedUpMember() throws Exception {
		String phone = randomPhone();
		verifyPhone(phone);
		signUp(phone).andExpect(status().isOk());
		return phone;
	}

	private void verifyPhone(String phone) throws Exception {
		postJson("/api/auth/send-code", Map.of("phoneNumber", phone)).andExpect(status().isOk());
		postJson("/api/auth/verify-phone", Map.of("phoneNumber", phone, "verificationCode", lastCodeSentTo(phone), "purpose", "SIGN_UP"))
				.andExpect(status().isOk());
	}

	private String lastCodeSentTo(String phone) {
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(smsSender, atLeastOnce()).send(eq(phone.replaceAll("[^0-9]", "")), text.capture());
		Matcher matcher = CODE.matcher(text.getValue());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private ResultActions signUp(String phone) throws Exception {
		return postJson("/api/auth/sign-up", Map.of(
				"name", "tester",
				"phoneNumber", phone,
				"loginPassword", PASSWORD,
				"initialAccountPassword", ACCOUNT_PASSWORD));
	}

	private String login(String phone, String password) throws Exception {
		return loginTokens(phone, password).get("accessToken").asText();
	}

	private JsonNode loginTokens(String phone, String password) throws Exception {
		String response = postJson("/api/auth/login", Map.of("phoneNumber", phone, "password", password))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response);
	}

	private ResultActions reissue(String refreshToken) throws Exception {
		return postJson("/api/auth/reissue", Map.of("refreshToken", refreshToken));
	}

	private ResultActions postJson(String url, Map<String, String> body) throws Exception {
		return mockMvc.perform(post(url)
				.with(fromIp(randomIp()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}
}
