package zb.accountMangement.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리자 화면의 종목 한 줄. 종목 정보와 그 종목에 걸린 미체결 주문 수
 */
@Getter
@AllArgsConstructor
public class AdminStockResponse {

	private Long id;

	private String code;

	private String name;

	private long currentPrice;

	private long pendingOrderCount;
}
