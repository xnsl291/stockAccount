package zb.accountMangement.stock.model.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "stock_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBalance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "stock_id", nullable = false)
  private Long stockId;

  private long avgPrice; // 평균 매수가 (원 미만 절사)

  private int quantity; // 수량

  private int heldQuantity; // 미체결 매도 주문에 묶여 팔거나 보낼 수 없는 수량

  public int getAvailableQuantity() {
    return quantity - heldQuantity;
  }

  /**
   * 수량을 더하고 평균단가를 다시 계산함. 수량이나 매수 총액이 넘치면 ArithmeticException
   */
  public void add(int addedQuantity, long unitPrice) {
    int newQuantity = Math.addExact(quantity, addedQuantity);
    long totalCost = Math.addExact(Math.multiplyExact(avgPrice, (long) quantity), Math.multiplyExact(unitPrice, (long) addedQuantity));
    avgPrice = totalCost / newQuantity;
    quantity = newQuantity;
  }
}
