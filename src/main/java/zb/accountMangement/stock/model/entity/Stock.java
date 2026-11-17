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
public class Stock {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String code; // 종목코드. 0으로 시작하는 코드가 있어 문자열로 저장

  private String name; // 종목명

  private long currentPrice; // 현재가 (원 단위)
}
