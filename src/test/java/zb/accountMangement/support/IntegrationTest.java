package zb.accountMangement.support;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import zb.accountMangement.account.service.AccountNumberGenerator;
import zb.accountMangement.common.service.RedisService;
import zb.accountMangement.member.service.SmsSender;

/**
 * MySQL·Redis 컨테이너를 띄워 실제와 같은 환경에서 돌리는 통합 테스트의 공통 부모 클래스
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	// 테스트 클래스마다 컨테이너를 다시 띄우지 않도록 JVM 하나에서 한 번만 시작하고 Ryuk가 정리하게 둔다
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	static {
		MYSQL.start();
		REDIS.start();
		grantLockInspection();
	}

	// 모든 테스트가 같은 스프링 컨텍스트를 재사용하도록 부모 클래스에 한 번만 둔다
	@MockitoBean
	protected SmsSender smsSender;

	// 평소에는 실제 Redis를 그대로 쓰고, 동시성 테스트만 특정 호출 직후에 멈춰 세우는 데 씀
	@MockitoSpyBean
	protected RedisService redisService;
	@MockitoSpyBean
	protected AccountNumberGenerator accountNumberGenerator;
	@MockitoSpyBean
	protected BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate lockInspector;

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("jwt.secret", () -> "dGVzdC1vbmx5LWp3dC1zZWNyZXQtZm9yLWludGVncmF0aW9uLXRlc3Rz");
	}

	// 동시성 테스트가 다른 트랜잭션이 잠금을 기다리는지 information_schema.innodb_trx로 확인하려면 PROCESS 권한이 필요함
	private static void grantLockInspection() {
		try {
			MYSQL.execInContainer("mysql", "-uroot", "-p" + MYSQL.getPassword(),
					"-e", "GRANT PROCESS ON *.* TO '" + MYSQL.getUsername() + "'@'%'");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 잠금을 기다리는 트랜잭션이 count개 이상 생길 때까지 기다림.
	 * 정해진 시간만 쉬면 다른 스레드의 요청이 잠금에 닿기 전에 테스트가 커밋해 버려 겹치는 순서를 만들지 못할 수 있음
	 */
	protected void awaitLockWaits(int count) throws InterruptedException {
		for (int i = 0; i < 300; i++) {
			Integer waiting = lockInspector.queryForObject(
					"select count(*) from information_schema.innodb_trx where trx_state = 'LOCK WAIT'", Integer.class);
			if (waiting != null && waiting >= count)
				return;
			Thread.sleep(100);
		}
		throw new AssertionError("30초 안에 잠금을 기다리는 트랜잭션이 " + count + "개 생기지 않음");
	}

	/**
	 * 요청 IP를 바꿈. 모든 테스트가 Redis 하나를 같이 써서, IP를 그대로 두면 IP별 문자 발송 한도를 서로 나눠 쓰게 됨
	 */
	protected static RequestPostProcessor fromIp(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

	protected static String randomIp() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(256);
	}
}
