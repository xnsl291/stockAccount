package zb.accountMangement.stock;

import java.util.Map;
import java.util.concurrent.Callable;
import javax.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 종목 ID만 받은 화면이 종목 이름과 현재가를 그릴 수 있는지 확인
 */
class StockLookupTest extends ApiTest {

	@Autowired
	EntityManagerFactory entityManagerFactory;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 종목_ID로_코드와_이름과_현재가를_조회한다() throws Exception {
		Member member = saveMember();
		Stock stock = saveStock(71_500);

		mockMvc.perform(as(member, get("/api/stocks/" + stock.getId() + "/info")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(stock.getId()))
				.andExpect(jsonPath("$.code").value(stock.getCode()))
				.andExpect(jsonPath("$.name").value(stock.getName()))
				.andExpect(jsonPath("$.currentPrice").value(71_500));
		mockMvc.perform(as(member, get("/api/stocks/" + Long.MAX_VALUE + "/info")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("STOCK_NOT_EXIST"));
		// 기존 현재가 조회는 숫자 하나를 그대로 돌려줌
		mockMvc.perform(as(member, get("/api/stocks/" + stock.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(71_500));
	}

	@Test
	void 보유_주식에_종목_이름과_현재가와_평가금액이_붙는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock stock = saveStock(1_000);
		buyNow(member, account, stock, 10);
		stock.setCurrentPrice(1_300);
		stockRepository.save(stock);

		mockMvc.perform(as(member, get("/api/stocks/" + account.getId() + "/balance")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].stockId").value(stock.getId()))
				.andExpect(jsonPath("$[0].stockCode").value(stock.getCode()))
				.andExpect(jsonPath("$[0].stockName").value(stock.getName()))
				.andExpect(jsonPath("$[0].currentPrice").value(1_300))
				.andExpect(jsonPath("$[0].evaluationAmount").value(13_000))
				.andExpect(jsonPath("$[0].quantity").value(10))
				.andExpect(jsonPath("$[0].avgPrice").value(1_000))
				.andExpect(jsonPath("$[0].profitNLoss").value(3_000));
	}

	@Test
	void 보유_주식은_현재가로_계산한_평가손익이_큰_순서로_나온다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock loss = saveStock(2_000);
		Stock gain = saveStock(1_000);
		buyNow(member, account, loss, 5);
		buyNow(member, account, gain, 10);
		loss.setCurrentPrice(1_500);
		gain.setCurrentPrice(1_300);
		stockRepository.save(loss);
		stockRepository.save(gain);

		mockMvc.perform(as(member, get("/api/stocks/" + account.getId() + "/balance")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].stockId").value(gain.getId()))
				.andExpect(jsonPath("$[0].profitNLoss").value(3_000))
				.andExpect(jsonPath("$[1].stockId").value(loss.getId()))
				.andExpect(jsonPath("$[1].profitNLoss").value(-2_500));
	}

	@Test
	void 평가손익은_응답에서만_계산하고_보유_주식_테이블에_칸을_두지_않는다() {
		Integer columns = jdbcTemplate.queryForObject("select count(*) from information_schema.columns"
				+ " where table_schema = database() and table_name = 'stock_balance' and column_name = 'profitnloss'", Integer.class);

		assertThat(columns).isZero();
	}

	@Test
	void 주문_내역에_종목_코드와_이름이_붙는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock stock = saveStock(1_000);

		order(member, account, stock, 900, 1)
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.stockCode").value(stock.getCode()))
				.andExpect(jsonPath("$.stockName").value(stock.getName()));

		mockMvc.perform(as(member, get("/api/stocks/" + account.getId() + "/history")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].stockId").value(stock.getId()))
				.andExpect(jsonPath("$[0].stockCode").value(stock.getCode()))
				.andExpect(jsonPath("$[0].stockName").value(stock.getName()));
	}

	@Test
	void 종목_수가_늘어도_보유_주식과_주문_내역_조회의_쿼리_수는_같다() throws Exception {
		Member member = saveMember();
		Account oneStock = saveAccount(member, 100_000);
		Account threeStocks = saveAccount(member, 100_000);
		buyNow(member, oneStock, saveStock(1_000), 1);
		for (int i = 0; i < 3; i++)
			buyNow(member, threeStocks, saveStock(1_000), 1);

		assertThat(queryCount(() -> balance(member, threeStocks)))
				.isEqualTo(queryCount(() -> balance(member, oneStock)));
		assertThat(queryCount(() -> history(member, threeStocks)))
				.isEqualTo(queryCount(() -> history(member, oneStock)));
	}

	private ResultActions balance(Member member, Account account) throws Exception {
		return mockMvc.perform(as(member, get("/api/stocks/" + account.getId() + "/balance"))).andExpect(status().isOk());
	}

	private ResultActions history(Member member, Account account) throws Exception {
		return mockMvc.perform(as(member, get("/api/stocks/" + account.getId() + "/history"))).andExpect(status().isOk());
	}

	/**
	 * 요청 하나가 DB에 보낸 SQL 문 수
	 */
	private long queryCount(Callable<?> request) throws Exception {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		statistics.setStatisticsEnabled(true);
		try {
			request.call();
			return statistics.getPrepareStatementCount();
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	private void buyNow(Member member, Account account, Stock stock, int quantity) throws Exception {
		order(member, account, stock, stock.getCurrentPrice(), quantity).andExpect(jsonPath("$.status").value("FILLED"));
	}

	private ResultActions order(Member member, Account account, Stock stock, long price, int quantity) throws Exception {
		return postJson("/api/stocks/buy", member, Map.of("accountId", account.getId(), "stockId", stock.getId(),
				"price", price, "quantity", quantity, "accountPassword", ACCOUNT_PASSWORD))
				.andExpect(status().isOk());
	}
}
