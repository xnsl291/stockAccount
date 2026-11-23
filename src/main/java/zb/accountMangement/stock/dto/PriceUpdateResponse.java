package zb.accountMangement.stock.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 시세 갱신 결과. 바뀐 종목 정보와, 새 시세로 미체결 주문을 체결해 본 결과
 */
@Getter
@Builder
public class PriceUpdateResponse {

	private Long id;

	private String code;

	private String name;

	private long currentPrice;

	private List<Long> filledOrderIds; // 이번 시세로 체결된 주문

	private List<Long> failedOrderIds; // 체결 도중 오류가 나 미체결로 남은 주문. 서버 로그에 원인이 남음

	private int remainingCount; // 시세 조건이 맞지 않거나 계좌가 거래정지라 체결하지 않은 미체결 주문 수

	public int getFilledCount() {
		return filledOrderIds.size();
	}

	public int getFailedCount() {
		return failedOrderIds.size();
	}
}
