package zb.accountMangement.common;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorResponseTest extends ApiTest {

	@Test
	void 잔액이_long_범위를_넘는_입금은_400이고_잔액은_그대로다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, Long.MAX_VALUE);

		postJson("/api/transactions/deposit", member, Map.of("accountId", account.getId(), "amount", 1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AMOUNT_OUT_OF_RANGE"));

		assertThat(balanceOf(account)).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	void 주문가와_수량의_곱이_long_범위를_넘으면_400() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Stock stock = saveStock(1_000);

		postJson("/api/stocks/buy", member, Map.of("accountId", account.getId(), "stockId", stock.getId(),
				"price", Long.MAX_VALUE, "quantity", 2, "accountPassword", ACCOUNT_PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AMOUNT_OUT_OF_RANGE"));

		assertThat(accountRepository.findById(account.getId()).orElseThrow().getHeldAmount()).isZero();
	}

	@Test
	void long으로_읽을_수_없는_금액은_400() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member);

		mockMvc.perform(as(member, post("/api/transactions/deposit"))
						.header("Idempotency-Key", "too-big")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accountId\": " + account.getId() + ", \"amount\": 99999999999999999999999}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

		assertThat(balanceOf(account)).isZero();
	}

	@Test
	void 스프링이_던지는_요청_오류도_같은_형식으로_응답한다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member);

		mockMvc.perform(as(member, get("/api/accounts/not-a-number")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
		mockMvc.perform(as(member, get("/api/stocks")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
		mockMvc.perform(as(member, get("/api/stocks/" + account.getId() + "/history").param("month", "13")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
				.andExpect(jsonPath("$.errors.month").exists());
		mockMvc.perform(as(member, put("/api/accounts/" + account.getId())))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}
}
