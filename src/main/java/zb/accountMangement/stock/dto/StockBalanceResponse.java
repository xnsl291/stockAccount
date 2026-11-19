package zb.accountMangement.stock.dto;

import lombok.Builder;
import lombok.Getter;
import zb.accountMangement.stock.model.entity.StockBalance;

@Getter
@Builder
public class StockBalanceResponse {

	private Long stockId;

	private int quantity;

	private int availableQuantity; // 미체결 매도 주문에 묶이지 않아 팔거나 보낼 수 있는 수량

	private long avgPrice;

	private long profitNLoss;

	public static StockBalanceResponse from(StockBalance stockBalance) {
		return StockBalanceResponse.builder()
				.stockId(stockBalance.getStockId())
				.quantity(stockBalance.getQuantity())
				.availableQuantity(stockBalance.getAvailableQuantity())
				.avgPrice(stockBalance.getAvgPrice())
				.profitNLoss(stockBalance.getProfitNLoss())
				.build();
	}
}
