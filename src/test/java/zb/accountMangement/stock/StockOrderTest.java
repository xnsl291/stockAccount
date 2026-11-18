package zb.accountMangement.stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.repository.TransactionRepository;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.dto.TradeStockDto;
import zb.accountMangement.stock.model.TradeType;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.stock.repository.TradingRepository;
import zb.accountMangement.stock.service.OrderService;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockOrderTest extends ApiTest {

	@Autowired
	OrderService orderService;
	@Autowired
	TradingRepository tradingRepository;
	@Autowired
	StockBalanceRepository stockBalanceRepository;
	@Autowired
	TransactionRepository transactionRepository;
	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void 시세가_주문가를_만족하면_주문가가_아닌_시세로_바로_체결된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock stock = saveStock(1_000);

		order("buy", member, account, stock, 1_200, 10)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FILLED"))
				.andExpect(jsonPath("$.price").value(1_200))
				.andExpect(jsonPath("$.filledPrice").value(1_000));

		assertThat(ledgerOf(account)).containsExactly("DEPOSIT 100000 100000", "STOCK_BUY -10000 90000", "FEE -1 89999");
		assertThat(heldAmountOf(account)).isZero();
		assertThat(holding(account, stock).getQuantity()).isEqualTo(10);
		assertThat(holding(account, stock).getAvgPrice()).isEqualTo(1_000);
	}

	@Test
	void 미체결_매수_주문은_돈을_묶어_같은_잔액으로_다시_주문하거나_출금할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Stock stock = saveStock(1_000);

		// 900원 x 10주 = 9,000원에 수수료 1원(9,000원의 0.015%에서 원 미만 버림)을 더해 9,001원이 묶임
		order("buy", member, account, stock, 900, 10)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));
		order("buy", member, account, stock, 900, 2)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EXCEED_BALANCE"));
		withdraw(member, account, 1_000)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EXCEED_BALANCE"));

		assertThat(balanceOf(account)).isEqualTo(10_000);
		assertThat(heldAmountOf(account)).isEqualTo(9_001);
		assertThat(ordersOf(account)).containsExactly("BUY PENDING");

		withdraw(member, account, 999).andExpect(status().isOk());
		assertThat(balanceOf(account)).isEqualTo(9_001);
	}

	@Test
	void 같은_계좌로_동시에_매수_주문을_내도_주문_가능_금액을_넘지_않는다() throws Exception {
		Account account = saveAccount(saveMember(), 10_000);
		Stock stock = saveStock(1_000);

		List<Long> orderIds = runConcurrently(10, i -> () -> {
			TradeStockDto tradeStockDto = new TradeStockDto();
			tradeStockDto.setAccountId(account.getId());
			tradeStockDto.setStockId(stock.getId());
			tradeStockDto.setPrice(900L);
			tradeStockDto.setQuantity(3);
			return orderService.placeOrder(TradeType.BUY, tradeStockDto);
		});

		// 한 건에 2,700원이 묶이므로 10,000원으로는 3건까지만 받을 수 있음
		assertThat(orderIds.stream().filter(Objects::nonNull)).hasSize(3);
		assertThat(heldAmountOf(account)).isEqualTo(8_100);
		assertThat(balanceOf(account)).isEqualTo(10_000);
		assertThat(ordersOf(account)).containsExactly("BUY PENDING", "BUY PENDING", "BUY PENDING");
	}

	@Test
	void 시세가_바뀌면_조건이_맞는_미체결_주문만_새_시세로_체결된다() throws Exception {
		Member buyer = saveMember();
		Account fillable = saveAccount(buyer, 10_000);
		Account tooLow = saveAccount(buyer, 10_000);
		Member seller = saveMember();
		Account sellerAccount = saveAccount(seller, 20_000);
		Stock stock = saveStock(1_000);
		order("buy", seller, sellerAccount, stock, 1_000, 10).andExpect(jsonPath("$.status").value("FILLED"));

		order("buy", buyer, fillable, stock, 900, 10).andExpect(jsonPath("$.status").value("PENDING"));
		order("buy", buyer, tooLow, stock, 800, 10).andExpect(jsonPath("$.status").value("PENDING"));
		order("sell", seller, sellerAccount, stock, 1_100, 10).andExpect(jsonPath("$.status").value("PENDING"));

		updatePrice(saveAdmin(), stock, 850).andExpect(status().isOk()).andExpect(jsonPath("$.currentPrice").value(850));

		assertThat(ordersOf(fillable)).containsExactly("BUY FILLED");
		assertThat(tradingRepository.search(fillable.getId(), null, null, null).get(0).getFilledPrice()).isEqualTo(850);
		assertThat(ledgerOf(fillable)).containsExactly("DEPOSIT 10000 10000", "STOCK_BUY -8500 1500", "FEE -1 1499");
		assertThat(heldAmountOf(fillable)).isZero();
		assertThat(holding(fillable, stock).getQuantity()).isEqualTo(10);
		assertThat(holding(fillable, stock).getAvgPrice()).isEqualTo(850);

		assertThat(ordersOf(tooLow)).containsExactly("BUY PENDING");
		assertThat(balanceOf(tooLow)).isEqualTo(10_000);
		assertThat(heldAmountOf(tooLow)).isEqualTo(8_001);
		assertThat(ordersOf(sellerAccount)).containsExactly("BUY FILLED", "SELL PENDING");

		updatePrice(saveAdmin(), stock, 1_200).andExpect(status().isOk());

		assertThat(ordersOf(sellerAccount)).containsExactly("BUY FILLED", "SELL FILLED");
		assertThat(ledgerOf(sellerAccount)).containsExactly(
				"DEPOSIT 20000 20000", "STOCK_BUY -10000 10000", "FEE -1 9999", "STOCK_SELL 12000 21999", "FEE -1 21998");
		assertThat(stockBalanceRepository.findByAccountIdAndStockId(sellerAccount.getId(), stock.getId())).isEmpty();
		assertThat(ordersOf(tooLow)).containsExactly("BUY PENDING");
	}

	@Test
	void 거래정지된_계좌의_주문은_남겨_두고_다른_계좌의_주문은_체결한다() throws Exception {
		Member suspendedMember = saveMember();
		Account suspended = saveAccount(suspendedMember, 10_000);
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Stock stock = saveStock(1_000);
		order("buy", suspendedMember, suspended, stock, 900, 10).andExpect(jsonPath("$.status").value("PENDING"));
		order("buy", member, account, stock, 900, 10).andExpect(jsonPath("$.status").value("PENDING"));
		Account toSuspend = accountRepository.findById(suspended.getId()).orElseThrow();
		suspend(toSuspend);
		accountRepository.save(toSuspend);

		updatePrice(saveAdmin(), stock, 900).andExpect(status().isOk());

		assertThat(ordersOf(suspended)).containsExactly("BUY PENDING");
		assertThat(balanceOf(suspended)).isEqualTo(10_000);
		assertThat(ordersOf(account)).containsExactly("BUY FILLED");
		assertThat(balanceOf(account)).isEqualTo(999);
	}

	@Test
	void 주문을_취소하면_묶인_돈이_풀리고_체결되거나_취소된_주문은_취소할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Stock stock = saveStock(1_000);
		long pendingOrderId = orderId(order("buy", member, account, stock, 900, 10));

		cancel(saveMember(), account, pendingOrderId)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MISMATCH_ACCOUNT_OWNER"));
		assertThat(heldAmountOf(account)).isEqualTo(9_001);

		cancel(member, account, pendingOrderId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(heldAmountOf(account)).isZero();
		assertThat(balanceOf(account)).isEqualTo(10_000);
		cancel(member, account, pendingOrderId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));

		long filledOrderId = orderId(order("buy", member, account, stock, 1_000, 5));
		cancel(member, account, filledOrderId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));

		// 취소한 주문은 시세가 조건에 맞게 바뀌어도 체결되지 않음
		updatePrice(saveAdmin(), stock, 500).andExpect(status().isOk());
		assertThat(ordersOf(account)).containsExactly("BUY CANCELLED", "BUY FILLED");
		assertThat(ledgerOf(account)).containsExactly("DEPOSIT 10000 10000", "STOCK_BUY -5000 5000");
	}

	@Test
	void 다른_계좌의_주문은_자기_계좌_경로로_취소할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Member other = saveMember();
		Account otherAccount = saveAccount(other);
		long orderId = orderId(order("buy", member, account, saveStock(1_000), 900, 10));

		cancel(other, otherAccount, orderId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_EXIST"));

		assertThat(ordersOf(account)).containsExactly("BUY PENDING");
		assertThat(heldAmountOf(account)).isEqualTo(9_001);
	}

	@Test
	void 미체결_매도_주문은_주식을_묶어_보유_수량보다_많이_팔_수_없고_취소하면_풀린다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 20_000);
		Stock stock = saveStock(1_000);
		order("buy", member, account, stock, 1_000, 10).andExpect(jsonPath("$.status").value("FILLED"));

		long sellOrderId = orderId(order("sell", member, account, stock, 1_500, 6));
		order("sell", member, account, stock, 1_500, 5)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
		assertThat(holding(account, stock).getQuantity()).isEqualTo(10);
		assertThat(holding(account, stock).getHeldQuantity()).isEqualTo(6);

		cancel(member, account, sellOrderId).andExpect(status().isOk());
		assertThat(holding(account, stock).getHeldQuantity()).isZero();
		order("sell", member, account, stock, 1_500, 5)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void 보유하지_않은_종목은_매도할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);

		order("sell", member, account, saveStock(1_000), 1_000, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NO_STOCK_BALANCE"));
	}

	@Test
	void 같은_키로_다시_보낸_주문은_한_번만_접수된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Stock stock = saveStock(1_000);
		String key = UUID.randomUUID().toString();
		Map<String, Object> buy = orderBody(account, stock, 900, 5);

		long first = orderId(postJson("/api/stocks/buy", member, key, buy));
		long second = orderId(postJson("/api/stocks/buy", member, key, buy));

		assertThat(second).isEqualTo(first);
		assertThat(ordersOf(account)).containsExactly("BUY PENDING");
		assertThat(heldAmountOf(account)).isEqualTo(4_500);

		postJson("/api/stocks/buy", member, key, orderBody(account, stock, 900, 6))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
	}

	@Test
	void 주문에_Idempotency_Key_헤더가_없으면_400() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);

		mockMvc.perform(as(member, post("/api/stocks/buy"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(orderBody(account, saveStock(1_000), 1_000, 1))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

		assertThat(ordersOf(account)).isEmpty();
	}

	@Test
	void 수량이_한도를_넘거나_가격이_0_이하이거나_없는_종목이면_주문을_거절한다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Stock stock = saveStock(1_000);

		for (String side : new String[] {"buy", "sell"}) {
			order(side, member, account, stock, 1_000, 0).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
			order(side, member, account, stock, 1_000, -1).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
			order(side, member, account, stock, 1, 1_000_001).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
			order(side, member, account, stock, 0, 1).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
			postJson("/api/stocks/" + side, member, Map.of("accountId", account.getId(), "stockId", Long.MAX_VALUE,
					"price", 1_000, "quantity", 1, "accountPassword", ACCOUNT_PASSWORD))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("STOCK_NOT_EXIST"));
		}

		assertThat(ordersOf(account)).isEmpty();
		assertThat(balanceOf(account)).isEqualTo(10_000);
	}

	@Test
	void 거래정지된_계좌로는_주문할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		account = accountRepository.findById(account.getId()).orElseThrow();
		suspend(account);
		accountRepository.save(account);

		order("buy", member, account, saveStock(1_000), 1_000, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PENDING_ACCOUNT"));

		assertThat(ordersOf(account)).isEmpty();
	}

	@Test
	void 시세_갱신과_종목_등록은_관리자만_할_수_있다() throws Exception {
		Member member = saveMember();
		Stock stock = saveStock(1_000);
		Map<String, Object> newStock = Map.of("code", "0" + randomPhone().substring(3, 8), "name", "신규상장", "currentPrice", 5_000);

		mockMvc.perform(as(member, put("/admin/stocks/" + stock.getId() + "/price"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("price", 1))))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(member, post("/admin/stocks"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(newStock)))
				.andExpect(status().isForbidden());

		assertThat(stockRepository.findById(stock.getId()).orElseThrow().getCurrentPrice()).isEqualTo(1_000);
		assertThat(stockRepository.existsByCode((String) newStock.get("code"))).isFalse();

		updatePrice(saveAdmin(), stock, 0).andExpect(status().isBadRequest());
		updatePrice(saveAdmin(), stock, 1_100).andExpect(status().isOk());
		assertThat(stockRepository.findById(stock.getId()).orElseThrow().getCurrentPrice()).isEqualTo(1_100);
	}

	@Test
	void 관리자가_등록한_종목은_0으로_시작하는_코드가_그대로_남고_코드나_이름으로_검색된다() throws Exception {
		Member admin = saveAdmin();
		Member member = saveMember();
		String code = "0" + randomPhone().substring(3, 8);
		String name = "검색전자" + code;
		Map<String, Object> newStock = Map.of("code", code, "name", name, "currentPrice", 5_000);

		postJson("/admin/stocks", admin, newStock)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(code));
		postJson("/admin/stocks", admin, newStock)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATED_STOCK_CODE"));
		postJson("/admin/stocks", admin, Map.of("code", "5930", "name", name, "currentPrice", 5_000))
				.andExpect(status().isBadRequest());

		mockMvc.perform(as(member, get("/api/stocks").param("keyword", code)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].code").value(code))
				.andExpect(jsonPath("$[0].currentPrice").value(5_000));
		mockMvc.perform(as(member, get("/api/stocks").param("keyword", name.substring(2))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value(name));
	}

	@Test
	void 같은_종목코드의_등록이_겹쳐도_500이_아니라_종목코드_중복_오류로_응답한다() throws Exception {
		Member admin = saveAdmin();
		String code = "0" + randomPhone().substring(3, 8);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<MvcResult> register;

		// 같은 코드의 종목을 저장만 하고 커밋하지 않은 상태를 만들어, 등록 요청이 코드 중복 검사는 통과하고 저장에서 기다리게 함
		TransactionStatus otherRegister = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			stockRepository.saveAndFlush(Stock.builder().code(code).name("먼저등록").currentPrice(1_000).build());
			register = executor.submit(() ->
					postJson("/admin/stocks", admin, Map.of("code", code, "name", "나중등록", "currentPrice", 5_000)).andReturn());
			awaitLockWaits(1);
		} finally {
			transactionManager.commit(otherRegister);
		}

		MvcResult result = register.get(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(result.getResponse().getStatus()).isEqualTo(409);
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
				.isEqualTo("DUPLICATED_STOCK_CODE");
	}

	@Test
	void 거래내역은_조건_없이도_조회되고_상태와_기간으로_걸러진다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Stock stock = saveStock(1_000);
		LocalDate before = LocalDate.now();
		order("buy", member, account, stock, 1_000, 1).andExpect(jsonPath("$.status").value("FILLED"));
		order("buy", member, account, stock, 900, 1).andExpect(jsonPath("$.status").value("PENDING"));
		LocalDate after = LocalDate.now();
		String url = "/api/stocks/" + account.getId() + "/history";

		mockMvc.perform(as(member, get(url)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].status").value("PENDING"));
		mockMvc.perform(as(member, get(url).param("status", "FILLED")))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].status").value("FILLED"));
		mockMvc.perform(as(member, get(url).param("year", String.valueOf(before.getYear() - 1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(as(member, get(url).param("month", "1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST_DATE"));
		mockMvc.perform(as(saveMember(), get(url))).andExpect(status().isForbidden());

		// 주문 사이에 달이 바뀌면 두 주문이 다른 달에 속하므로 월별 조회는 확인하지 않음
		assumeTrue(before.withDayOfMonth(1).equals(after.withDayOfMonth(1)));
		mockMvc.perform(as(member, get(url)
						.param("year", String.valueOf(after.getYear())).param("month", String.valueOf(after.getMonthValue()))))
				.andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(as(member, get(url).param("year", String.valueOf(after.getYear()))))
				.andExpect(jsonPath("$.length()").value(2));
	}

	private ResultActions order(String side, Member member, Account account, Stock stock, long price, int quantity) throws Exception {
		return postJson("/api/stocks/" + side, member, orderBody(account, stock, price, quantity));
	}

	private Map<String, Object> orderBody(Account account, Stock stock, long price, int quantity) {
		return Map.of("accountId", account.getId(), "stockId", stock.getId(), "price", price, "quantity", quantity,
				"accountPassword", ACCOUNT_PASSWORD);
	}

	private long orderId(ResultActions result) throws Exception {
		String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asLong();
	}

	private ResultActions cancel(Member member, Account account, long orderId) throws Exception {
		return mockMvc.perform(as(member, delete("/api/stocks/" + account.getId() + "/orders/" + orderId)));
	}

	private ResultActions updatePrice(Member admin, Stock stock, long price) throws Exception {
		return mockMvc.perform(as(admin, put("/admin/stocks/" + stock.getId() + "/price"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("price", price))));
	}

	private ResultActions withdraw(Member member, Account account, long amount) throws Exception {
		return postJson("/api/transactions/withdrawal", member,
				Map.of("accountId", account.getId(), "amount", amount, "accountPassword", ACCOUNT_PASSWORD));
	}

	// 계좌 상태 setter를 열어 두지 않으므로, 비밀번호를 연속으로 틀린 것처럼 만들어 거래정지시킴
	private void suspend(Account account) {
		while (account.isExistsAccount())
			account.recordPasswordFailure();
	}

	private long heldAmountOf(Account account) {
		return accountRepository.findById(account.getId()).orElseThrow().getHeldAmount();
	}

	private StockBalance holding(Account account, Stock stock) {
		return stockBalanceRepository.findByAccountIdAndStockId(account.getId(), stock.getId()).orElseThrow();
	}

	private List<String> ordersOf(Account account) {
		return tradingRepository.findAll().stream()
				.filter(trading -> trading.getAccountId().equals(account.getId()))
				.sorted((x, y) -> x.getId().compareTo(y.getId()))
				.map(trading -> trading.getType() + " " + trading.getStatus())
				.collect(Collectors.toList());
	}

	private List<String> ledgerOf(Account account) {
		return transactionRepository.findAll().stream()
				.filter(transaction -> transaction.getAccountId().equals(account.getId()))
				.sorted((x, y) -> x.getId().compareTo(y.getId()))
				.map(transaction -> transaction.getType() + " " + transaction.getAmount() + " " + transaction.getBalanceAfter())
				.collect(Collectors.toList());
	}
}
