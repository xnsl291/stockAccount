package zb.accountMangement.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.repository.AccountRepository;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.repository.MemberRepository;
import zb.accountMangement.support.IntegrationTest;

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

class SignUpTest extends IntegrationTest {

	private static final String PASSWORD = "password123";
	private static final String ACCOUNT_PASSWORD = "1234";
	private static final Pattern CODE = Pattern.compile("\\[(\\d+)]");

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	MemberRepository memberRepository;
	@Autowired
	AccountRepository accountRepository;
	@Autowired
	BCryptPasswordEncoder passwordEncoder;

	@Test
	void 문자_인증_후_가입하면_로그인해서_인증이_필요한_API를_호출할_수_있다() throws Exception {
		String phone = randomPhone();
		verifyPhone(phone);

		signUp(phone).andExpect(status().isOk());

		Member member = memberRepository.findByPhoneNumber(phone).orElseThrow();
		assertThat(member.getPassword()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, member.getPassword())).isTrue();

		List<Account> accounts = accountRepository.findByMemberId(member.getId());
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
	void 인증번호가_틀리면_인증되지_않는다() throws Exception {
		String phone = randomPhone();
		postJson("/api/auth/send-code", Map.of("phoneNumber", phone)).andExpect(status().isOk());

		postJson("/api/auth/verify-phone", Map.of("phoneNumber", phone, "verificationCode", "wrong"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNMATCHED_VERIFICATION_CODE"));
		signUp(phone).andExpect(status().isBadRequest());
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
	void 회원정보를_수정하면_새_비밀번호가_암호화되어_저장된다() throws Exception {
		String phone = signedUpMember();
		String accessToken = login(phone, PASSWORD);
		Member member = memberRepository.findByPhoneNumber(phone).orElseThrow();

		mockMvc.perform(patch("/api/member/" + member.getId())
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("name", "newname", "phoneNumber", phone, "password", "newpassword"))))
				.andExpect(status().isOk());

		assertThat(memberRepository.findById(member.getId()).orElseThrow().getPassword()).isNotEqualTo("newpassword");
		login(phone, "newpassword");
	}

	@Test
	void 문자_인증으로_비밀번호를_재설정하면_새_비밀번호로_로그인된다() throws Exception {
		String phone = signedUpMember();
		String accessToken = login(phone, PASSWORD);
		Member member = memberRepository.findByPhoneNumber(phone).orElseThrow();

		mockMvc.perform(post("/api/member/find-pw/" + member.getId())
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("name", member.getName(), "phoneNumber", phone))))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/member/find-pw/" + member.getId() + "/confirm")
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("inputCode", lastCodeSentTo(phone), "newPassword", "resetpassword"))))
				.andExpect(status().isOk());

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
		postJson("/api/auth/verify-phone", Map.of("phoneNumber", phone, "verificationCode", lastCodeSentTo(phone)))
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
		String response = postJson("/api/auth/login", Map.of("phoneNumber", phone, "password", password))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).get("accessToken").asText();
	}

	private ResultActions postJson(String url, Map<String, String> body) throws Exception {
		return mockMvc.perform(post(url)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private String randomPhone() {
		return "010" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
	}
}
