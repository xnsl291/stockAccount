package zb.accountMangement.stock.model.entity;

import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBalance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long accountId;

  private Long stockId;

  private long avgPrice; // 평균 매수가 (원 미만 절사)

  private long profitNLoss; // 평가손익

  private int quantity; // 수량

  private int heldQuantity; // 미체결 매도 주문에 묶여 팔거나 보낼 수 없는 수량

  public int getAvailableQuantity() {
    return quantity - heldQuantity;
  }

  /**
   * 수량을 더하고 평균단가를 다시 계산
   * @param addedQuantity - 늘어난 수량
   * @param unitPrice - 늘어난 수량의 단가
   */
  public void add(int addedQuantity, long unitPrice) {
    avgPrice = (avgPrice * quantity + unitPrice * addedQuantity) / (quantity + addedQuantity);
    quantity += addedQuantity;
  }
}
