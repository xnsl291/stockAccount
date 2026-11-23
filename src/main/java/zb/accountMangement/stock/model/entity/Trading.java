package zb.accountMangement.stock.model.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import zb.accountMangement.stock.model.OrderStatus;
import zb.accountMangement.stock.model.TradeType;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trading {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long accountId;

  private Long stockId;

  private long price;   // 주문가 (원 단위)

  private int quantity; // 수량

  @Enumerated(EnumType.STRING)
  private TradeType type; // 매매종류

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private OrderStatus status = OrderStatus.PENDING;

  private Long filledPrice; // 체결가 (원 단위). 체결 전에는 null

  @CreatedDate
  private LocalDateTime createdAt; // 주문 시각

  private LocalDateTime tradeAt; // 체결 시각

  /**
   * 지정가 주문이 이 시세에서 체결되는지 - 매수는 시세가 주문가 이하, 매도는 주문가 이상일 때
   */
  public boolean isFillableAt(long currentPrice) {
    return type == TradeType.BUY ? currentPrice <= price : currentPrice >= price;
  }

  public void fill(long currentPrice) {
    status = OrderStatus.FILLED;
    filledPrice = currentPrice;
    tradeAt = LocalDateTime.now();
  }
}
