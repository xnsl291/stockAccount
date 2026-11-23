package zb.accountMangement.stock.dto;

import lombok.Builder;
import lombok.Getter;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.model.TradeType;
import zb.accountMangement.stock.model.entity.Trading;

import java.time.LocalDateTime;

/**
 * 주문·이체 기록 응답
 */
@Getter
@Builder
public class TradingResponse {

	private Long id;

	private Long accountId;

	private Long stockId;

	private TradeType type;

	private OrderStatus status;

	private long price; // 주문가

	private int quantity; // 이체 기록은 보낸 쪽이 음수

	private Long filledPrice; // 체결 전에는 null

	private LocalDateTime createdAt;

	private LocalDateTime tradeAt;

	public static TradingResponse from(Trading trading) {
		return TradingResponse.builder()
				.id(trading.getId())
				.accountId(trading.getAccountId())
				.stockId(trading.getStockId())
				.type(trading.getType())
				.status(trading.getStatus())
				.price(trading.getPrice())
				.quantity(trading.getQuantity())
				.filledPrice(trading.getFilledPrice())
				.createdAt(trading.getCreatedAt())
				.tradeAt(trading.getTradeAt())
				.build();
	}
}
