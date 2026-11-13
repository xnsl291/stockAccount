package zb.accountMangement.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

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

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.redis.host", REDIS::getHost);
		registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
	}
}
