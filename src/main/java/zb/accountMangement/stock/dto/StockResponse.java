package zb.accountMangement.stock.dto;

import lombok.Builder;
import lombok.Getter;
import zb.accountMangement.stock.model.entity.Stock;

@Getter
@Builder
public class StockResponse {

	private Long id;

	private String code;

	private String name;

	private long currentPrice;

	public static StockResponse from(Stock stock) {
		return StockResponse.builder()
				.id(stock.getId())
				.code(stock.getCode())
				.name(stock.getName())
				.currentPrice(stock.getCurrentPrice())
				.build();
	}
}
