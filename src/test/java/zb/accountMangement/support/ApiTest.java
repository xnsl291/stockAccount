package zb.accountMangement.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
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
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.member.model.RoleType;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.member.repository.MemberRepository;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.repository.StockRepository;

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
	@Autowired
	protected StockRepository stockRepository;

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

	protected Member saveAdmin() {
		return memberRepository.save(Member.builder()
				.name("admin")
				.phoneNumber(randomPhone())
				.password(passwordEncoder.encode("password123"))
				.role(RoleType.ADMIN)
				.build());
	}

	protected Stock saveStock(long currentPrice) {
		return stockRepository.save(Stock.builder()
				.code(String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)))
				.name("테스트전자")
				.currentPrice(currentPrice)
				.build());
	}

	protected String randomPhone() {
		return "010" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
	}

	/**
	 * 여러 스레드가 같은 순간에 작업을 시작하게 한 뒤 결과를 모음. 업무 규칙에 걸려 거절된 작업의 결과는 null
	 */
	protected <T> List<T> runConcurrently(int threads, IntFunction<Callable<T>> taskOf) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<T>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<T> task = taskOf.apply(i);
			futures.add(executor.submit(() -> {
				start.await();
				try {
					return task.call();
				} catch (CustomException e) {
					return null;
				}
			}));
		}
		start.countDown();
		List<T> results = new ArrayList<>();
		for (Future<T> future : futures)
			results.add(future.get(30, TimeUnit.SECONDS));
		executor.shutdown();
		return results;
	}
}
