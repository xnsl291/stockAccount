package zb.accountMangement.stock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.common.type.RoleType;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.stock.repository.TradingRepository;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockTransferTest extends ApiTest {

	@Autowired
	StockBalanceRepository stockBalanceRepository;
	@Autowired
	TradingRepository tradingRepository;
	@Autowired
	JdbcTemplate jdbcTemplate;
	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void 같은_계좌의_같은_종목_보유_내역은_두_줄로_저장되지_않는다() {
		Account account = saveAccount(saveMember());
		Stock stock = saveStock(1_000);
		stockBalanceRepository.saveAndFlush(StockBalance.builder().accountId(account.getId()).stockId(stock.getId()).quantity(1).build());

		assertThatThrownBy(() -> stockBalanceRepository.saveAndFlush(
				StockBalance.builder().accountId(account.getId()).stockId(stock.getId()).quantity(1).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 다른_회원에게_보내면_보내는_쪽이_시세의_1퍼센트를_수수료로_내고_받는_쪽은_평균단가를_이어받는다() throws Exception {
		Member sender = saveMember();
		Account senderAccount = saveAccount(sender, 100_000);
		Member receiver = saveMember();
		Account receiverAccount = saveAccount(receiver, 100_000);
		Stock stock = saveStock(1_000);
		buy(sender, senderAccount, stock, 1_000, 10);
		updatePrice(stock, 2_000);
		buy(receiver, receiverAccount, stock, 2_000, 4);
		long senderBalance = balanceOf(senderAccount);
		long receiverBalance = balanceOf(receiverAccount);

		transfer(sender, senderAccount, receiverAccount, stock, 4)
				.andExpect(status().isOk()).andExpect(content().string("이체완료"));

		// 수수료는 2,000원 x 4주의 1%인 80원
		assertThat(balanceOf(senderAccount)).isEqualTo(senderBalance - 80).isEqualTo(ledgerSum(senderAccount));
		assertThat(balanceOf(receiverAccount)).isEqualTo(receiverBalance);
		assertThat(holding(senderAccount, stock)).get().extracting(StockBalance::getQuantity, StockBalance::getAvgPrice)
				.containsExactly(6, 1_000L);
		// 받는 쪽이 2,000원에 산 4주와 1,000원에 넘겨받은 4주의 평균
		assertThat(holding(receiverAccount, stock)).get().extracting(StockBalance::getQuantity, StockBalance::getAvgPrice)
				.containsExactly(8, 1_500L);
		assertThat(tradesOf(senderAccount)).containsExactly("BUY FILLED 10", "TRANSFER FILLED -4");
		assertThat(tradesOf(receiverAccount)).containsExactly("BUY FILLED 4", "TRANSFER FILLED 4");
	}

	@Test
	void 같은_회원의_계좌끼리는_수수료_없이_옮기고_전부_옮기면_보내는_쪽_보유_내역이_없어진다() throws Exception {
		Member member = saveMember();
		Account from = saveAccount(member, 100_000);
		Account to = saveAccount(member);
		Stock stock = saveStock(1_000);
		buy(member, from, stock, 1_000, 10);
		long balance = balanceOf(from);

		transfer(member, from, to, stock, 10).andExpect(status().isOk());

		assertThat(balanceOf(from)).isEqualTo(balance);
		assertThat(holding(from, stock)).isEmpty();
		assertThat(holding(to, stock)).get().extracting(StockBalance::getQuantity, StockBalance::getAvgPrice)
				.containsExactly(10, 1_000L);
	}

	@Test
	void 받는_쪽_수량이_int_범위를_넘으면_400이고_아무것도_바뀌지_않는다() throws Exception {
		Member member = saveMember();
		Account from = saveAccount(member);
		Account to = saveAccount(member);
		Stock stock = saveStock(1_000);
		stockBalanceRepository.save(StockBalance.builder().accountId(from.getId()).stockId(stock.getId()).quantity(1).avgPrice(1_000).build());
		stockBalanceRepository.save(StockBalance.builder().accountId(to.getId()).stockId(stock.getId()).quantity(Integer.MAX_VALUE).avgPrice(1_000).build());

		transfer(member, from, to, stock, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AMOUNT_OUT_OF_RANGE"));

		assertThat(holding(from, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(1);
		assertThat(holding(to, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	void 받는_쪽_매수_총액이_long_범위를_넘으면_400이다() throws Exception {
		Member member = saveMember();
		Account from = saveAccount(member);
		Account to = saveAccount(member);
		Stock stock = saveStock(1_000);
		stockBalanceRepository.save(StockBalance.builder().accountId(from.getId()).stockId(stock.getId()).quantity(1).avgPrice(1_000).build());
		stockBalanceRepository.save(StockBalance.builder().accountId(to.getId()).stockId(stock.getId()).quantity(2).avgPrice(Long.MAX_VALUE / 2 + 1).build());

		transfer(member, from, to, stock, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AMOUNT_OUT_OF_RANGE"));
	}

	@Test
	void 같은_계좌나_해지된_계좌로는_보낼_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Member other = saveMember();
		Account closed = saveAccount(other);
		saveAccount(other);
		mockMvc.perform(as(other, delete("/api/accounts/" + closed.getId()))).andExpect(content().string("true"));
		Stock stock = saveStock(1_000);
		buy(member, account, stock, 1_000, 10);
		long balance = balanceOf(account);

		transfer(member, account, account, stock, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));
		transfer(member, account, closed, stock, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ACCOUNT"));

		assertThat(holding(account, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(10);
		assertThat(holding(closed, stock)).isEmpty();
		assertThat(balanceOf(account)).isEqualTo(balance);
	}

	@Test
	void 미체결_매도에_묶인_주식과_보유하지_않은_주식은_보낼_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Account receiverAccount = saveAccount(saveMember());
		Stock stock = saveStock(1_000);
		buy(member, account, stock, 1_000, 10);
		postJson("/api/stocks/sell", member, orderBody(account, stock, 1_500, 8)).andExpect(jsonPath("$.status").value("PENDING"));

		transfer(member, account, receiverAccount, stock, 3)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
		transfer(member, account, receiverAccount, saveStock(1_000), 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NO_STOCK_BALANCE"));
		assertThat(holding(receiverAccount, stock)).isEmpty();

		transfer(member, account, receiverAccount, stock, 2).andExpect(status().isOk());
		assertThat(holding(account, stock)).get().extracting(StockBalance::getQuantity, StockBalance::getHeldQuantity)
				.containsExactly(8, 8);
		assertThat(holding(receiverAccount, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(2);
	}

	@Test
	void 수수료를_낼_돈이_없으면_주식도_넘어가지_않는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_001);
		Account receiverAccount = saveAccount(saveMember());
		Stock stock = saveStock(1_000);
		buy(member, account, stock, 1_000, 10);
		assertThat(balanceOf(account)).isZero();

		transfer(member, account, receiverAccount, stock, 5)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EXCEED_BALANCE"));

		assertThat(holding(account, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(10);
		assertThat(holding(receiverAccount, stock)).isEmpty();
	}

	@Test
	void 같은_키로_다시_보낸_이체는_한_번만_실행된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Account receiverAccount = saveAccount(saveMember());
		Stock stock = saveStock(1_000);
		buy(member, account, stock, 1_000, 10);
		String key = UUID.randomUUID().toString();
		Map<String, Object> body = transferBody(account, receiverAccount, stock, 3);

		postJson("/api/stocks/transfer", member, key, body).andExpect(status().isOk()).andExpect(content().string("이체완료"));
		postJson("/api/stocks/transfer", member, key, body).andExpect(status().isOk()).andExpect(content().string("이체완료"));

		assertThat(holding(account, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(7);
		assertThat(holding(receiverAccount, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(3);
		assertThat(balanceOf(account)).isEqualTo(100_000 - 10_001 - 30);
	}

	@Test
	void 성공한_이체를_같은_키로_다시_보내면_그사이_비밀번호가_바뀌었거나_틀려도_처음_응답을_받고_틀린_횟수가_오르지_않는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 100_000);
		Account receiverAccount = saveAccount(saveMember());
		Stock stock = saveStock(1_000);
		buy(member, account, stock, 1_000, 10);
		String key = UUID.randomUUID().toString();
		Map<String, Object> body = transferBody(account, receiverAccount, stock, 3);
		postJson("/api/stocks/transfer", member, key, body).andExpect(status().isOk());

		changeAccountPassword(account, "5678");
		for (String password : List.of(ACCOUNT_PASSWORD, "0000"))
			postJson("/api/stocks/transfer", member, key, withPassword(body, password))
					.andExpect(status().isOk()).andExpect(content().string("이체완료"));

		assertThat(passwordFailCountOf(account)).isZero();
		assertThat(holding(account, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(7);
	}

	@Test
	void 매수_매도_취소_이체를_섞어_해도_거래_기록의_합이_잔액과_같고_묶인_돈이_남지_않는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 1_000_000);
		Member other = saveMember();
		Account otherAccount = saveAccount(other, 500_000);
		Stock stock = saveStock(10_000);

		buy(member, account, stock, 10_000, 30);
		long cancelledOrderId = orderId(postJson("/api/stocks/buy", member, orderBody(account, stock, 9_000, 10)));
		postJson("/api/stocks/buy", other, orderBody(otherAccount, stock, 9_500, 20)).andExpect(jsonPath("$.status").value("PENDING"));
		postJson("/api/stocks/sell", member, orderBody(account, stock, 12_000, 10)).andExpect(jsonPath("$.status").value("PENDING"));
		mockMvc.perform(as(member, delete("/api/stocks/" + account.getId() + "/orders/" + cancelledOrderId)))
				.andExpect(status().isOk());
		updatePrice(stock, 9_300);
		updatePrice(stock, 12_500);
		transfer(member, account, otherAccount, stock, 5).andExpect(status().isOk());
		postJson("/api/transactions/withdrawal", member,
				Map.of("accountId", account.getId(), "amount", 50_000, "accountPassword", ACCOUNT_PASSWORD))
				.andExpect(status().isOk());

		// 매수 300,000 + 수수료 45, 매도 125,000 - 수수료 18, 이체 수수료 625(12,500원 x 5주의 1%), 출금 50,000
		assertThat(balanceOf(account)).isEqualTo(1_000_000 - 300_045 + 124_982 - 625 - 50_000).isEqualTo(ledgerSum(account));
		// 9,300원 x 20주 매수 186,000 + 수수료 27
		assertThat(balanceOf(otherAccount)).isEqualTo(500_000 - 186_027).isEqualTo(ledgerSum(otherAccount));
		assertThat(accountRepository.findById(account.getId()).orElseThrow().getHeldAmount()).isZero();
		assertThat(accountRepository.findById(otherAccount.getId()).orElseThrow().getHeldAmount()).isZero();
		assertThat(holding(account, stock)).get().extracting(StockBalance::getQuantity, StockBalance::getHeldQuantity)
				.containsExactly(15, 0);
		assertThat(holding(otherAccount, stock)).get().extracting(StockBalance::getQuantity).isEqualTo(25);
		assertThat(tradesOf(account)).containsExactly(
				"BUY FILLED 30", "BUY CANCELLED 10", "SELL FILLED 10", "TRANSFER FILLED -5");
	}

	@Test
	void 주식이_들어오는_중에_탈퇴하면_들어온_주식을_보고_탈퇴를_거절한다() throws Exception {
		Stock stock = saveStock(1_000);
		Member receiver = saveMember();
		Account receiverAccount = saveAccount(receiver);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Integer> signOut;

		// 주식 이체가 받는 계좌를 잠그고 보유 내역을 넣은 채 아직 커밋하지 않은 상태를 만들고, 그동안 탈퇴를 시작함
		TransactionStatus incomingTransfer = transactionManager.getTransaction(new DefaultTransactionDefinition());
		try {
			accountRepository.findByIdForUpdate(receiverAccount.getId()).orElseThrow();
			stockBalanceRepository.saveAndFlush(StockBalance.builder()
					.accountId(receiverAccount.getId()).stockId(stock.getId()).quantity(3).avgPrice(1_000).build());
			signOut = executor.submit(() ->
					mockMvc.perform(as(receiver, delete("/api/auth/sign-out"))).andReturn().getResponse().getStatus());
			awaitLockWaits(1);
		} finally {
			transactionManager.commit(incomingTransfer);
		}

		assertThat(signOut.get(30, TimeUnit.SECONDS)).isEqualTo(400);
		executor.shutdown();
		assertThat(accountRepository.findById(receiverAccount.getId()).orElseThrow().isExistsAccount()).isTrue();
		assertThat(memberRepository.findById(receiver.getId()).orElseThrow().getRole()).isEqualTo(RoleType.USER);
	}

	private void buy(Member member, Account account, Stock stock, long price, int quantity) throws Exception {
		postJson("/api/stocks/buy", member, orderBody(account, stock, price, quantity))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FILLED"));
	}

	private Map<String, Object> orderBody(Account account, Stock stock, long price, int quantity) {
		return Map.of("accountId", account.getId(), "stockId", stock.getId(), "price", price, "quantity", quantity,
				"accountPassword", ACCOUNT_PASSWORD);
	}

	private long orderId(ResultActions result) throws Exception {
		String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asLong();
	}

	private ResultActions transfer(Member member, Account from, Account to, Stock stock, int quantity) throws Exception {
		return postJson("/api/stocks/transfer", member, transferBody(from, to, stock, quantity));
	}

	private Map<String, Object> transferBody(Account from, Account to, Stock stock, int quantity) {
		return Map.of("senderAccountId", from.getId(), "receiverAccountId", to.getId(), "stockId", stock.getId(),
				"quantity", quantity, "accountPassword", ACCOUNT_PASSWORD);
	}

	private void updatePrice(Stock stock, long price) throws Exception {
		mockMvc.perform(as(saveAdmin(), put("/admin/stocks/" + stock.getId() + "/price"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("price", price))))
				.andExpect(status().isOk());
	}

	private Optional<StockBalance> holding(Account account, Stock stock) {
		return stockBalanceRepository.findByAccountIdAndStockId(account.getId(), stock.getId());
	}

	private long ledgerSum(Account account) {
		return jdbcTemplate.queryForObject(
				"select coalesce(sum(amount), 0) from transaction where account_id = ?", Long.class, account.getId());
	}

	private List<String> tradesOf(Account account) {
		return tradingRepository.findAll().stream()
				.filter(trading -> trading.getAccountId().equals(account.getId()))
				.sorted((x, y) -> x.getId().compareTo(y.getId()))
				.map(trading -> trading.getType() + " " + trading.getStatus() + " " + trading.getQuantity())
				.collect(Collectors.toList());
	}
}
