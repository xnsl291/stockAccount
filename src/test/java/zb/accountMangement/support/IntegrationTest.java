package zb.accountMangement.support;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
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
	}

	// 모든 테스트가 같은 스프링 컨텍스트를 재사용하도록 부모 클래스에 한 번만 둔다
	@MockBean
	protected SmsSender smsSender;

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.redis.host", REDIS::getHost);
		registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("jwt.secret", () -> "dGVzdC1vbmx5LWp3dC1zZWNyZXQtZm9yLWludGVncmF0aW9uLXRlc3Rz");
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
