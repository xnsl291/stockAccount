package zb.accountMangement.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zb.accountMangement.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지표 경로가 관리 포트에서만 토큰 없이 열리고 업무 포트에서는 열리지 않는지 실제 포트로 확인함
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
// 스프링 부트 테스트는 기본으로 지표 내보내기를 꺼 두므로 이 테스트에서만 켬
@AutoConfigureMetrics
@AutoConfigureTestRestTemplate
class MonitoringEndpointTest extends IntegrationTest {

	@LocalServerPort
	private int serverPort;

	@LocalManagementPort
	private int managementPort;

	@Autowired
	private TestRestTemplate rest;

	@Test
	void 관리_포트에서는_토큰_없이_지표를_읽는다() {
		ResponseEntity<String> response = rest.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/prometheus", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("hikaricp_connections_pending", "jvm_memory_used_bytes");
	}

	@Test
	void 관리_포트의_상태_확인은_세부_정보_없이_UP만_보인다() {
		ResponseEntity<String> response = rest.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
	}

	@Test
	void 업무_포트에서는_토큰_없이_지표_경로가_열리지_않는다() {
		ResponseEntity<String> response = rest.getForEntity("http://127.0.0.1:" + serverPort + "/actuator/prometheus", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
