package zb.accountMangement.stock.dto;

import lombok.Builder;
import lombok.Getter;
import zb.accountMangement.stock.model.entity.Stock;
import zb.accountMangement.stock.model.entity.StockBalance;

@Getter
@Builder
public class StockBalanceResponse {

	private Long stockId;

	private String stockCode;

	private String stockName;

	private int quantity;

	private int availableQuantity; // 미체결 매도 주문에 묶이지 않아 팔거나 보낼 수 있는 수량

	private long avgPrice;

	private long currentPrice;

	private long evaluationAmount; // 현재가 x 수량. 화면 표시용이며 원장에 남는 값이 아님

	private long profitNLoss; // (현재가 - 평균단가) x 수량

	public static StockBalanceResponse of(StockBalance stockBalance, Stock stock) {
		long quantity = stockBalance.getQuantity();
		return StockBalanceResponse.builder()
				.stockId(stockBalance.getStockId())
				.stockCode(stock.getCode())
				.stockName(stock.getName())
				.quantity(stockBalance.getQuantity())
				.availableQuantity(stockBalance.getAvailableQuantity())
				.avgPrice(stockBalance.getAvgPrice())
				.currentPrice(stock.getCurrentPrice())
				.evaluationAmount(Math.multiplyExact(stock.getCurrentPrice(), quantity))
				.profitNLoss(Math.multiplyExact(Math.subtractExact(stock.getCurrentPrice(), stockBalance.getAvgPrice()), quantity))
				.build();
	}
}
