package zb.accountMangement.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import zb.accountMangement.account.model.TransactionType;
import zb.accountMangement.account.model.entity.Account;
import zb.accountMangement.account.service.AccountService;
import zb.accountMangement.account.service.TransactionService;
import zb.accountMangement.common.exception.CustomException;
import zb.accountMangement.common.type.ErrorCode;
import zb.accountMangement.stock.dto.DateDto;
import zb.accountMangement.stock.dto.PriceUpdateResponse;
import zb.accountMangement.stock.dto.TradeStockDto;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.model.TradeType;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;
import zb.accountMangement.stock.model.entity.Trading;
import zb.accountMangement.stock.repository.StockBalanceRepository;
import zb.accountMangement.stock.repository.StockRepository;
import zb.accountMangement.stock.repository.TradingRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static zb.accountMangement.common.type.FeeRate.TRADE_FEE_RATE;

/**
 * 지정가 주문의 접수·체결·취소.
 * 주문을 받으면 매수는 돈을, 매도는 주식을 묶어 두고, 체결되거나 취소될 때 풀어 줌.
 * 계좌의 돈과 보유 주식은 계좌 행을 잠근 트랜잭션만 바꾸며, 잠금은 트랜잭션의 첫 조회여야 함.
 * 잠금을 기다리기 전에 다른 조회를 하면 MySQL이 그 시점의 스냅샷을 고정해,
 * 앞선 트랜잭션이 바꾼 보유 주식을 보지 못함
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
	private final AccountService accountService;
	private final TransactionService transactionService;
	private final StockRepository stockRepository;
	private final StockBalanceRepository stockBalanceRepository;
	private final TradingRepository tradingRepository;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 주문 접수 - 시세가 이미 주문가를 만족하면 바로 체결하고, 아니면 미체결로 남김
	 * @param type - 매수 또는 매도
	 * @param tradeStockDto - 주문 dto (계좌 ID, 주식 ID, 주문가, 수량)
	 * @return 주문 ID
	 */
	@Transactional
	public Long placeOrder(TradeType type, TradeStockDto tradeStockDto) {
		Account account = accountService.getAccountForUpdate(tradeStockDto.getAccountId());
		if (!account.isExistsAccount())
			throw new CustomException(ErrorCode.INVALID_ACCOUNT);
		Stock stock = stockRepository.findById(tradeStockDto.getStockId())
				.orElseThrow(() -> new CustomException(ErrorCode.STOCK_NOT_EXIST));

		Trading order = Trading.builder()
				.accountId(account.getId())
				.stockId(stock.getId())
				.price(tradeStockDto.getPrice())
				.quantity(tradeStockDto.getQuantity())
				.type(type)
				.build();

		if (type == TradeType.BUY) {
			// 묶을 금액이 쓸 수 있는 잔액보다 크면 EXCEED_BALANCE로 거절됨
			account.hold(buyHold(order));
		} else {
			StockBalance stockBalance = getStockBalance(order);
			if (stockBalance.getAvailableQuantity() < order.getQuantity())
				throw new CustomException(ErrorCode.INSUFFICIENT_STOCK);
			stockBalance.setHeldQuantity(stockBalance.getHeldQuantity() + order.getQuantity());
		}
		tradingRepository.save(order);

		if (order.isFillableAt(stock.getCurrentPrice()))
			fill(order, account, stock);
		return order.getId();
	}

	/**
	 * 주문 취소 - 미체결 주문만 취소할 수 있고, 묶어 둔 돈이나 주식을 풀어 줌
	 * @param accountId - 주문을 낸 계좌 ID
	 * @param orderId - 주문 ID
	 * @return 취소된 주문
	 */
	@Transactional
	public Trading cancelOrder(Long accountId, Long orderId) {
		Account account = accountService.getAccountForUpdate(accountId);
		Trading order = tradingRepository.findById(orderId)
				.filter(found -> found.getAccountId().equals(accountId))
				.orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_EXIST));
		if (order.getStatus() != OrderStatus.PENDING)
			throw new CustomException(ErrorCode.ORDER_NOT_PENDING);

		if (order.getType() == TradeType.BUY) {
			account.releaseHold(buyHold(order));
		} else {
			StockBalance stockBalance = getStockBalance(order);
			stockBalance.setHeldQuantity(stockBalance.getHeldQuantity() - order.getQuantity());
		}
		order.setStatus(OrderStatus.CANCELLED);
		return order;
	}

	/**
	 * 종목의 미체결 주문 중 지금 시세로 체결되는 주문을 먼저 들어온 순서대로 체결
	 * @param stock - 시세가 바뀐 종목
	 * @return 종목 정보와 체결·실패한 주문 ID, 체결하지 않고 남긴 주문 수
	 */
	public PriceUpdateResponse matchPendingOrders(Stock stock) {
		List<Trading> pendings = tradingRepository.findByStockIdAndStatusOrderByIdAsc(stock.getId(), OrderStatus.PENDING);
		List<Long> filled = new ArrayList<>();
		List<Long> failed = new ArrayList<>();
		for (Trading pending : pendings) {
			// 주문마다 트랜잭션을 따로 열어, 한 주문의 체결이 실패해도 나머지 주문은 체결되게 함
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status -> fillIfFillable(pending.getAccountId(), pending.getId()))))
					filled.add(pending.getId());
			} catch (RuntimeException e) {
				log.error("주문 체결 실패 - 주문 ID: {}", pending.getId(), e);
				failed.add(pending.getId());
			}
		}
		return PriceUpdateResponse.builder()
				.id(stock.getId())
				.code(stock.getCode())
				.name(stock.getName())
				.currentPrice(stock.getCurrentPrice())
				.filledOrderIds(filled)
				.failedOrderIds(failed)
				.remainingCount(pendings.size() - filled.size() - failed.size())
				.build();
	}

	private boolean fillIfFillable(Long accountId, Long orderId) {
		Account account = accountService.getAccountForUpdate(accountId);
		// 목록을 읽은 뒤 취소되었거나 다른 시세 갱신이 먼저 체결했을 수 있어, 계좌를 잠근 뒤 주문과 시세를 다시 읽음
		Trading order = tradingRepository.findById(orderId).orElseThrow();
		Stock stock = stockRepository.findById(order.getStockId()).orElseThrow();
		if (order.getStatus() != OrderStatus.PENDING || !account.isExistsAccount()
				|| !order.isFillableAt(stock.getCurrentPrice()))
			return false;
		fill(order, account, stock);
		return true;
	}

	/**
	 * 주문가가 아닌 현재 시세로 체결하고, 묶어 둔 돈이나 주식을 풀어 실제 대금과 수수료를 반영
	 */
	private void fill(Trading order, Account account, Stock stock) {
		long amount = Math.multiplyExact(stock.getCurrentPrice(), order.getQuantity());

		if (order.getType() == TradeType.BUY) {
			account.releaseHold(buyHold(order));
			transactionService.changeBalance(account.getId(), TransactionType.STOCK_BUY, amount, stock.getName(), null);

			StockBalance stockBalance = stockBalanceRepository.findByAccountIdAndStockId(account.getId(), stock.getId())
					.orElseGet(() -> StockBalance.builder().accountId(account.getId()).stockId(stock.getId()).build());
			stockBalance.add(order.getQuantity(), stock.getCurrentPrice());
			stockBalanceRepository.save(stockBalance);
		} else {
			StockBalance stockBalance = getStockBalance(order);
			stockBalance.setHeldQuantity(stockBalance.getHeldQuantity() - order.getQuantity());
			stockBalance.setQuantity(stockBalance.getQuantity() - order.getQuantity());
			if (stockBalance.getQuantity() == 0)
				stockBalanceRepository.delete(stockBalance);
			transactionService.changeBalance(account.getId(), TransactionType.STOCK_SELL, amount, stock.getName(), null);
		}

		long fee = TRADE_FEE_RATE.of(amount);
		if (fee > 0)
			transactionService.changeBalance(account.getId(), TransactionType.FEE, fee, "주식 매매 수수료", stock.getName());
		order.fill(stock.getCurrentPrice());
	}

	/**
	 * 미체결 매수 주문이 묶어 두는 금액.
	 * 매수는 주문가 이하로만 체결되므로 주문가로 계산한 대금과 수수료를 묶어 두면 체결할 때 모자라지 않음
	 */
	private long buyHold(Trading order) {
		long amount = Math.multiplyExact(order.getPrice(), order.getQuantity());
		return Math.addExact(amount, TRADE_FEE_RATE.of(amount));
	}

	private StockBalance getStockBalance(Trading order) {
		return stockBalanceRepository.findByAccountIdAndStockId(order.getAccountId(), order.getStockId())
				.orElseThrow(() -> new CustomException(ErrorCode.NO_STOCK_BALANCE));
	}

	/**
	 * 주문 조회
	 * @param orderId - 주문 ID
	 * @return 주문
	 */
	public Trading getOrder(Long orderId) {
		return tradingRepository.findById(orderId)
				.orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_EXIST));
	}

	/**
	 * 주문·거래내역 조회 - 조건을 주지 않으면 전체를 최근 순으로 돌려줌
	 * @param accountId - 계좌 ID
	 * @param status - 주문 상태 (없으면 전체)
	 * @param dateDto - 조회할 연도와 월 (연도만 주면 그 해 전체, 둘 다 없으면 전체 기간)
	 * @return 거래내역
	 */
	public List<Trading> getTradeHistory(Long accountId, OrderStatus status, DateDto dateDto) {
		if (dateDto.getYear() == null) {
			if (dateDto.getMonth() != null)
				throw new CustomException(ErrorCode.INVALID_REQUEST_DATE);
			return tradingRepository.search(accountId, status, null, null);
		}

		boolean wholeYear = dateDto.getMonth() == null;
		LocalDateTime from = LocalDateTime.of(dateDto.getYear(), wholeYear ? 1 : dateDto.getMonth(), 1, 0, 0);
		if (from.isAfter(LocalDateTime.now()))
			throw new CustomException(ErrorCode.INVALID_REQUEST_DATE);
		return tradingRepository.search(accountId, status, from, wholeYear ? from.plusYears(1) : from.plusMonths(1));
	}
}
