package zb.accountMangement.account;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import zb.accountMangement.account.dto.DepositDto;
import zb.accountMangement.account.dto.TransferDto;
import zb.accountMangement.account.model.AccountStatus;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.model.entity.Transaction;
import zb.accountMangement.account.repository.TransactionRepository;
import zb.accountMangement.idempotency.service.IdempotencyService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.member.model.entity.Member;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.support.ApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionConsistencyTest extends ApiTest {

	@Autowired
	TransactionService transactionService;
	@Autowired
	IdempotencyService idempotencyService;
	@Autowired
	TransactionRepository transactionRepository;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void API로_동시에_출금을_요청해도_잔액만큼만_출금된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);

		List<Integer> statuses = runConcurrently(10, i -> () ->
				withdraw(member, account, 10_000).andReturn().getResponse().getStatus());

		assertThat(statuses).filteredOn(status -> status == 200).hasSize(5);
		assertThat(statuses).filteredOn(status -> status == 400).hasSize(5);
		assertThat(balanceOf(account)).isZero();
		assertThat(ledgerSum(account)).isZero();
	}

	@Test
	void 두_계좌가_서로에게_동시에_송금해도_교착_없이_모두_처리되고_합계가_보존된다() throws Exception {
		Account a = saveAccount(saveMember(), 100_000);
		Account b = saveAccount(saveMember(), 100_000);

		List<String> results = runConcurrently(20, i -> () -> {
			TransferDto transferDto = new TransferDto();
			transferDto.setAmount(i % 2 == 0 ? 1_000L : 2_000L);
			return i % 2 == 0
					? transactionService.transfer(a.getId(), b.getId(), transferDto)
					: transactionService.transfer(b.getId(), a.getId(), transferDto);
		});

		assertThat(results).doesNotContainNull();
		assertThat(balanceOf(a)).isEqualTo(110_000);
		assertThat(balanceOf(b)).isEqualTo(90_000);
		assertThat(ledgerSum(a)).isEqualTo(110_000);
		assertThat(ledgerSum(b)).isEqualTo(90_000);
	}

	@Test
	void 성공한_출금과_송금을_같은_키로_다시_보내면_그사이_비밀번호가_바뀌었거나_틀려도_처음_응답을_받고_틀린_횟수가_오르지_않는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 50_000);
		Account receiver = saveAccount(saveMember());
		String withdrawalKey = UUID.randomUUID().toString();
		String transferKey = UUID.randomUUID().toString();
		Map<String, Object> withdrawal = Map.of("accountId", account.getId(), "amount", 10_000, "accountPassword", ACCOUNT_PASSWORD);
		Map<String, Object> transfer = Map.of("amount", 10_000, "accountPassword", ACCOUNT_PASSWORD);
		String transferUrl = "/api/transactions/" + account.getId() + "/transfer/" + receiver.getId();
		postJson("/api/transactions/withdrawal", member, withdrawalKey, withdrawal).andExpect(status().isOk());
		postJson(transferUrl, member, transferKey, transfer).andExpect(status().isOk());

		changeAccountPassword(account, "5678");
		// 바뀌기 전 비밀번호와 아예 틀린 비밀번호 모두 지금은 틀린 비밀번호임
		for (String password : List.of(ACCOUNT_PASSWORD, "0000")) {
			postJson("/api/transactions/withdrawal", member, withdrawalKey, withPassword(withdrawal, password))
					.andExpect(status().isOk()).andExpect(content().string("출금완료"));
			postJson(transferUrl, member, transferKey, withPassword(transfer, password))
					.andExpect(status().isOk()).andExpect(content().string("송금완료"));
		}

		assertThat(passwordFailCountOf(account)).isZero();
		assertThat(balanceOf(account)).isEqualTo(30_000);
		assertThat(balanceOf(receiver)).isEqualTo(10_000);
		assertThat(ledgerOf(account)).containsExactly("DEPOSIT 50000 50000", "WITHDRAW -10000 40000", "TRANSFER_OUT -10000 30000");
	}

	@Test
	void 같은_키의_요청이_동시에_들어와도_한_번만_실행된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member);
		String key = UUID.randomUUID().toString();
		DepositDto depositDto = new DepositDto();
		depositDto.setAccountId(account.getId());
		depositDto.setAmount(10_000L);

		// 먼저 키를 저장한 요청이 커밋하기 전에 나머지 4개가 같은 키 저장에서 기다리게 해, 실제로 겹친 경우만 확인함
		List<String> results = runConcurrently(5, i -> () -> idempotencyService.execute(member.getId(), key, "DEPOSIT", () -> {
			String result = transactionService.deposit(depositDto);
			try {
				awaitLockWaits(4);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
			return result;
		}));

		assertThat(results).containsOnly("입금완료").hasSize(5);
		assertThat(balanceOf(account)).isEqualTo(10_000);
		assertThat(ledgerOf(account)).hasSize(1);
	}

	@Test
	void 같은_키를_다른_내용의_요청에_다시_쓰면_거절한다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member);
		String key = UUID.randomUUID().toString();

		deposit(member, key, account, 10_000).andExpect(status().isOk());
		deposit(member, key, account, 99_000)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

		assertThat(balanceOf(account)).isEqualTo(10_000);
	}

	@Test
	void 키는_회원별로_구분되어_다른_회원이_같은_키를_써도_각자_실행된다() throws Exception {
		Member first = saveMember();
		Member second = saveMember();
		Account firstAccount = saveAccount(first);
		Account secondAccount = saveAccount(second);
		String key = UUID.randomUUID().toString();

		deposit(first, key, firstAccount, 10_000).andExpect(status().isOk());
		deposit(second, key, secondAccount, 20_000).andExpect(status().isOk());

		assertThat(balanceOf(firstAccount)).isEqualTo(10_000);
		assertThat(balanceOf(secondAccount)).isEqualTo(20_000);
	}

	@Test
	void 실패한_요청은_키가_남지_않아_같은_키로_다시_시도할_수_있다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 5_000);
		String key = UUID.randomUUID().toString();
		Map<String, Object> withdrawal = Map.of("accountId", account.getId(), "amount", 10_000, "accountPassword", ACCOUNT_PASSWORD);

		postJson("/api/transactions/withdrawal", member, key, withdrawal)
				.andExpect(jsonPath("$.code").value("EXCEED_BALANCE"));
		deposit(member, UUID.randomUUID().toString(), account, 5_000).andExpect(status().isOk());
		postJson("/api/transactions/withdrawal", member, key, withdrawal).andExpect(status().isOk());

		assertThat(balanceOf(account)).isZero();
	}

	@Test
	void Idempotency_Key_헤더가_없으면_400() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member);

		mockMvc.perform(as(member, post("/api/transactions/deposit"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("accountId", account.getId(), "amount", 10_000))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

		assertThat(balanceOf(account)).isZero();
	}

	@Test
	void 입금_출금_송금을_섞어_해도_거래_기록의_합이_잔액과_같다() throws Exception {
		Member member = saveMember();
		Member other = saveMember();
		Account account = saveAccount(member, 100_000);
		Account otherAccount = saveAccount(other);

		withdraw(member, account, 30_000).andExpect(status().isOk());
		transfer(member, account, otherAccount, 20_000).andExpect(status().isOk());
		transfer(other, otherAccount, account, 5_000).andExpect(status().isOk());
		withdraw(member, account, 999_999).andExpect(jsonPath("$.code").value("EXCEED_BALANCE"));

		assertThat(ledgerOf(account)).containsExactly(
				"DEPOSIT 100000 100000", "WITHDRAW -30000 70000", "TRANSFER_OUT -20000 50000", "TRANSFER_IN 5000 55000");
		assertThat(ledgerOf(otherAccount)).containsExactly("TRANSFER_IN 20000 20000", "TRANSFER_OUT -5000 15000");
		assertThat(balanceOf(account)).isEqualTo(55_000).isEqualTo(ledgerSum(account));
		assertThat(balanceOf(otherAccount)).isEqualTo(15_000).isEqualTo(ledgerSum(otherAccount));
	}

	@Test
	void 주식_매수와_매도_대금도_거래_기록에_남는다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Stock stock = saveStock(1_000);
		Map<String, Object> order = Map.of("accountId", account.getId(), "stockId", stock.getId(), "price", 1_000,
				"quantity", 3, "accountPassword", ACCOUNT_PASSWORD);

		postJson("/api/stocks/buy", member, order).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FILLED"));
		postJson("/api/stocks/sell", member, order).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FILLED"));

		assertThat(ledgerOf(account)).containsExactly("DEPOSIT 10000 10000", "STOCK_BUY -3000 7000", "STOCK_SELL 3000 10000");
		assertThat(balanceOf(account)).isEqualTo(10_000);
	}

	@Test
	void 잔액보다_많이_출금하거나_송금할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Account receiver = saveAccount(saveMember());

		withdraw(member, account, 10_001)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EXCEED_BALANCE"));
		transfer(member, account, receiver, 10_001)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EXCEED_BALANCE"));

		assertThat(balanceOf(account)).isEqualTo(10_000);
		assertThat(balanceOf(receiver)).isZero();
	}

	@Test
	void 자기_계좌로는_송금할_수_없다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);

		transfer(member, account, account, 1_000)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));

		assertThat(balanceOf(account)).isEqualTo(10_000);
	}

	@Test
	void 금액이_0이거나_음수면_거절한다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		Account receiver = saveAccount(saveMember());

		for (long amount : new long[] {0, -1_000}) {
			expectInvalidArgument(deposit(member, UUID.randomUUID().toString(), account, amount));
			expectInvalidArgument(withdraw(member, account, amount));
			expectInvalidArgument(transfer(member, account, receiver, amount));
		}

		assertThat(balanceOf(account)).isEqualTo(10_000);
		assertThat(balanceOf(receiver)).isZero();
	}

	@Test
	void 해지한_계좌로는_입금과_송금을_받을_수_없다() throws Exception {
		Member member = saveMember();
		Account closed = saveAccount(member);
		saveAccount(member);
		Member sender = saveMember();
		Account senderAccount = saveAccount(sender, 10_000);
		mockMvc.perform(as(member, delete("/api/accounts/" + closed.getId()))).andExpect(content().string("true"));

		deposit(member, UUID.randomUUID().toString(), closed, 1_000).andExpect(status().isNotFound());
		transfer(sender, senderAccount, closed, 1_000)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ACCOUNT"));

		assertThat(balanceOf(closed)).isZero();
		assertThat(balanceOf(senderAccount)).isEqualTo(10_000);
	}

	@Test
	void 잔액이_남은_계좌는_해지할_수_없고_잔액을_비우면_해지된다() throws Exception {
		Member member = saveMember();
		Account account = saveAccount(member, 10_000);
		saveAccount(member);

		mockMvc.perform(as(member, delete("/api/accounts/" + account.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REMAINING_ASSET"));
		assertThat(statusOf(account)).isEqualTo(AccountStatus.EXISTS);

		withdraw(member, account, 10_000).andExpect(status().isOk());
		mockMvc.perform(as(member, delete("/api/accounts/" + account.getId())))
				.andExpect(status().isOk()).andExpect(content().string("true"));
		assertThat(statusOf(account)).isEqualTo(AccountStatus.DELETED);
	}

	private ResultActions deposit(Member member, String key, Account account, long amount) throws Exception {
		return postJson("/api/transactions/deposit", member, key, Map.of("accountId", account.getId(), "amount", amount));
	}

	private ResultActions withdraw(Member member, Account account, long amount) throws Exception {
		return postJson("/api/transactions/withdrawal", member,
				Map.of("accountId", account.getId(), "amount", amount, "accountPassword", ACCOUNT_PASSWORD));
	}

	private ResultActions transfer(Member member, Account from, Account to, long amount) throws Exception {
		return postJson("/api/transactions/" + from.getId() + "/transfer/" + to.getId(), member,
				Map.of("amount", amount, "accountPassword", ACCOUNT_PASSWORD));
	}

	private void expectInvalidArgument(ResultActions result) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
	}

	private AccountStatus statusOf(Account account) {
		return accountRepository.findById(account.getId()).orElseThrow().getStatus();
	}

	private long ledgerSum(Account account) {
		return jdbcTemplate.queryForObject(
				"select coalesce(sum(amount), 0) from transaction where account_id = ?", Long.class, account.getId());
	}

	private List<String> ledgerOf(Account account) {
		return transactionRepository.findAll().stream()
				.filter(transaction -> transaction.getAccountId().equals(account.getId()))
				.sorted((x, y) -> x.getId().compareTo(y.getId()))
				.map(transaction -> transaction.getType() + " " + transaction.getAmount() + " " + transaction.getBalanceAfter())
				.collect(Collectors.toList());
	}
}
