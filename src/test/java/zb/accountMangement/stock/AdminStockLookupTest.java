package zb.accountMangement.stock;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자가 종목과 미체결 주문을 보는 조회
 */
class AdminStockLookupTest extends ApiTest {

	@Autowired
	StockBalanceRepository stockBalanceRepository;

	@Test
	void 관리자는_종목_목록을_미체결_주문_수와_함께_최근_등록_순으로_본다() throws Exception {
		Member admin = saveAdmin();
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock withOrders = saveStock(1_000);
		order(member, account, withOrders, 900, 1);
		order(member, account, withOrders, 800, 1);
		order(member, account, withOrders, 1_000, 1); // 바로 체결돼 미체결 수에 들어가지 않음
		cancel(member, account, order(member, account, withOrders, 700, 1));
		Stock noOrders = saveStock(2_000);

		mockMvc.perform(as(admin, get("/admin/stocks").param("page", "0").param("size", "2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].id").value(noOrders.getId()))
				.andExpect(jsonPath("$.content[0].pendingOrderCount").value(0))
				.andExpect(jsonPath("$.content[1].id").value(withOrders.getId()))
				.andExpect(jsonPath("$.content[1].code").value(withOrders.getCode()))
				.andExpect(jsonPath("$.content[1].name").value(withOrders.getName()))
				.andExpect(jsonPath("$.content[1].currentPrice").value(1_000))
				.andExpect(jsonPath("$.content[1].pendingOrderCount").value(2));

		mockMvc.perform(as(admin, get("/admin/stocks/" + withOrders.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(withOrders.getCode()))
				.andExpect(jsonPath("$.pendingOrderCount").value(2));
		mockMvc.perform(as(admin, get("/admin/stocks/" + Long.MAX_VALUE)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STOCK_NOT_EXIST"));
		mockMvc.perform(as(admin, get("/admin/stocks").param("size", "101")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 관리자는_종목별_미체결_주문을_먼저_들어온_순서로_본다() throws Exception {
		Member admin = saveAdmin();
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock stock = saveStock(1_000);
		long first = order(member, account, stock, 900, 3);
		long filled = order(member, account, stock, 1_000, 1);
		long second = order(member, account, stock, 800, 2);
		Stock other = saveStock(1_000);
		order(member, account, other, 900, 1);

		mockMvc.perform(as(admin, get("/admin/stocks/" + stock.getId() + "/orders")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(first))
				.andExpect(jsonPath("$[0].type").value("BUY"))
				.andExpect(jsonPath("$[0].price").value(900))
				.andExpect(jsonPath("$[0].quantity").value(3))
				.andExpect(jsonPath("$[0].stockName").value(stock.getName()))
				.andExpect(jsonPath("$[1].id").value(second));
		mockMvc.perform(as(admin, get("/admin/stocks/" + stock.getId() + "/orders").param("status", "FILLED")))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(filled));
		mockMvc.perform(as(admin, get("/admin/stocks/" + Long.MAX_VALUE + "/orders")))
				.andExpect(status().isNotFound());
	}

	@Test
	void 시세를_넣으면_체결된_주문과_실패한_주문과_남은_주문_수를_돌려준다() throws Exception {
		Member admin = saveAdmin();
		Member member = saveMember();
		Account fillSeller = saveAccount(member, 100_000);
		Account failSeller = saveAccount(member, 100_000);
		Account buyer = saveAccount(member, 100_000);
		Stock stock = saveStock(1_000);
		order(member, fillSeller, stock, 1_000, 10);
		order(member, failSeller, stock, 1_000, 10);
		long fills = order("sell", member, fillSeller, stock, 1_050, 5);
		long fails = order("sell", member, failSeller, stock, 1_100, 5);
		long remains = order(member, buyer, stock, 900, 1);
		// 보유 내역이 사라진 계좌의 매도 주문은 체결 도중 오류가 남
		stockBalanceRepository.delete(stockBalanceRepository.findByAccountIdAndStockId(failSeller.getId(), stock.getId()).orElseThrow());

		mockMvc.perform(as(admin, put("/admin/stocks/" + stock.getId() + "/price"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("price", 1_100))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(stock.getId()))
				.andExpect(jsonPath("$.currentPrice").value(1_100))
				.andExpect(jsonPath("$.filledCount").value(1))
				.andExpect(jsonPath("$.filledOrderIds[0]").value(fills))
				.andExpect(jsonPath("$.failedCount").value(1))
				.andExpect(jsonPath("$.failedOrderIds[0]").value(fails))
				.andExpect(jsonPath("$.remainingCount").value(1));

		mockMvc.perform(as(admin, get("/admin/stocks/" + stock.getId() + "/orders")))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(fails))
				.andExpect(jsonPath("$[1].id").value(remains));
	}

	@Test
	void 관리자_API는_관리자만_쓸_수_있다() throws Exception {
		Member member = saveMember();
		Stock stock = saveStock(1_000);
		Map<String, Object> newStock = Map.of("code", "0" + randomPhone().substring(3, 8), "name", "신규상장", "currentPrice", 5_000);

		mockMvc.perform(as(member, get("/admin/stocks"))).andExpect(status().isForbidden());
		mockMvc.perform(as(member, get("/admin/stocks/" + stock.getId()))).andExpect(status().isForbidden());
		mockMvc.perform(as(member, get("/admin/stocks/" + stock.getId() + "/orders"))).andExpect(status().isForbidden());
		mockMvc.perform(as(member, put("/admin/stocks/" + stock.getId() + "/price"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("price", 1))))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(member, post("/admin/stocks"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(newStock)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/stocks")).andExpect(status().isUnauthorized());

		assertThat(stockRepository.findById(stock.getId()).orElseThrow().getCurrentPrice()).isEqualTo(1_000);
		assertThat(stockRepository.existsByCode((String) newStock.get("code"))).isFalse();

		mockMvc.perform(as(saveAdmin(), put("/admin/stocks/" + stock.getId() + "/price"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("price", 0))))
				.andExpect(status().isBadRequest());
	}

	private long order(Member member, Account account, Stock stock, long price, int quantity) throws Exception {
		return order("buy", member, account, stock, price, quantity);
	}

	private long order(String side, Member member, Account account, Stock stock, long price, int quantity) throws Exception {
		String body = postJson("/api/stocks/" + side, member, Map.of("accountId", account.getId(), "stockId", stock.getId(),
				"price", price, "quantity", quantity, "accountPassword", ACCOUNT_PASSWORD))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asLong();
	}

	private ResultActions cancel(Member member, Account account, long orderId) throws Exception {
		return mockMvc.perform(as(member, delete("/api/stocks/" + account.getId() + "/orders/" + orderId)))
				.andExpect(status().isOk());
	}
}
