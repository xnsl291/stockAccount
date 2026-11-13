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

  private Double avgPrice; // 평균 매수가

  private Double profitNLoss; // 평가손익

  private int quantity; // 수량
}
