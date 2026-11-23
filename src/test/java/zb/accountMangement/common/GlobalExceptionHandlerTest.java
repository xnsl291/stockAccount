package zb.accountMangement.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import zb.accountMangement.common.exception.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LockFailingController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void 교착_상태나_잠금_대기_초과는_다시_시도하라는_409로_응답한다() throws Exception {
		mockMvc.perform(get("/deadlock"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_REQUEST"));
		mockMvc.perform(get("/lock-timeout"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_REQUEST"));
	}

	@RestController
	static class LockFailingController {

		@GetMapping("/deadlock")
		String deadlock() {
			throw new CannotAcquireLockException("Deadlock found when trying to get lock");
		}

		@GetMapping("/lock-timeout")
		String lockTimeout() {
			throw new PessimisticLockingFailureException("Lock wait timeout exceeded");
		}
	}
}
