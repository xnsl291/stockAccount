package zb.accountMangement.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.common.auth.JwtTokenProvider;
import zb.accountMangement.common.type.RoleType;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.repository.MemberRepository;
import zb.accountMangement.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtAuthenticationTest extends IntegrationTest {

	private static final String PASSWORD = "password123";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	MemberRepository memberRepository;
	@Autowired
	BCryptPasswordEncoder passwordEncoder;
	@Value("${jwt.secret}")
	String jwtSecret;

	@Test
	void 로그인한_토큰으로_인증이_필요한_API를_호출할_수_있다() throws Exception {
		Member member = saveMember(RoleType.USER);
		String accessToken = login(member).get("accessToken").asText();

		getMember(member.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(member.getId()));
	}

	@Test
	void 토큰이_없으면_401() throws Exception {
		mockMvc.perform(get("/api/member/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("FAILED_VALIDATION"));
	}

	@Test
	void 위조된_토큰은_401() throws Exception {
		getMember(1L, "not-a-jwt")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void 만료된_토큰은_401() throws Exception {
		Member member = saveMember(RoleType.USER);
		String expired = Jwts.builder()
				.setSubject(member.getId().toString())
				.claim("role", "USER")
				.claim("type", "access")
				.setExpiration(new Date(System.currentTimeMillis() - 1000))
				.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
				.compact();

		getMember(member.getId(), expired)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
	}

	@Test
	void 리프레시_토큰은_액세스_토큰으로_쓸_수_없다() throws Exception {
		Member member = saveMember(RoleType.USER);
		String refreshToken = login(member).get("refreshToken").asText();

		getMember(member.getId(), refreshToken)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 권한이_다르면_403() throws Exception {
		Member admin = saveMember(RoleType.ADMIN);
		String accessToken = login(admin).get("accessToken").asText();

		getMember(admin.getId(), accessToken)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void 로그아웃하면_액세스_토큰과_리프레시_토큰을_더_쓸_수_없다() throws Exception {
		Member member = saveMember(RoleType.USER);
		JsonNode tokens = login(member);
		String accessToken = tokens.get("accessToken").asText();

		mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());

		getMember(member.getId(), accessToken)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		reissue(tokens.get("refreshToken").asText())
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 관리자도_로그아웃할_수_있고_로그아웃한_토큰으로는_관리자_API를_쓸_수_없다() throws Exception {
		Member admin = saveMember(RoleType.ADMIN);
		String accessToken = login(admin).get("accessToken").asText();

		mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk());

		mockMvc.perform(get("/admin/stocks").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void 관리자는_로그아웃만_열리고_탈퇴_경로는_막힌다() throws Exception {
		Member admin = saveMember(RoleType.ADMIN);
		String accessToken = login(admin).get("accessToken").asText();

		mockMvc.perform(delete("/api/auth/sign-out").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void 리프레시_토큰으로_재발급받고_이전_리프레시_토큰은_다시_쓸_수_없다() throws Exception {
		Member member = saveMember(RoleType.USER);
		String oldRefreshToken = login(member).get("refreshToken").asText();

		JsonNode reissued = objectMapper.readTree(reissue(oldRefreshToken)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());

		getMember(member.getId(), reissued.get("accessToken").asText())
				.andExpect(status().isOk());
		reissue(oldRefreshToken)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 같은_리프레시_토큰으로_동시에_재발급을_요청해도_한_번만_발급된다() throws Exception {
		Member member = saveMember(RoleType.USER);
		String refreshToken = login(member).get("refreshToken").asText();
		ExecutorService executor = Executors.newFixedThreadPool(5);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 5; i++)
			results.add(executor.submit(() -> {
				start.await();
				return reissue(refreshToken).andReturn().getResponse().getStatus();
			}));
		start.countDown();

		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> result : results)
			statuses.add(result.get(30, TimeUnit.SECONDS));
		executor.shutdown();
		assertThat(statuses).containsOnlyOnce(200).containsOnly(200, 401);
	}

	@Test
	void JWT_비밀키가_비어_있으면_시작하지_않는다() {
		assertThatThrownBy(() -> new JwtTokenProvider(null, "", 1800, 1209600))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("jwt.secret");
	}

	private Member saveMember(RoleType role) {
		return memberRepository.save(Member.builder()
				.name("tester")
				.phoneNumber("010" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000))
				.password(passwordEncoder.encode(PASSWORD))
				.role(role)
				.build());
	}

	private JsonNode login(Member member) throws Exception {
		String body = objectMapper.writeValueAsString(Map.of("phoneNumber", member.getPhoneNumber(), "password", PASSWORD));
		String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response);
	}

	private ResultActions getMember(Long memberId, String accessToken) throws Exception {
		return mockMvc.perform(get("/api/member/" + memberId).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions reissue(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/reissue")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
	}
}
