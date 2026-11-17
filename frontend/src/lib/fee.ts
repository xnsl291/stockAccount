// 서버와 같은 식으로 화면에서 미리 계산하는 값. 화면 값은 "예상"으로만 보여 주고 실제 값은 서버를 다시 읽음
// 서버: FeeRate.of(amount) = amount × 비율, 원 미만 버림 (BigDecimal, RoundingMode.DOWN)
// 큰 금액에서 소수 오차가 나지 않게 정수(BigInt)로 계산함

/** 매매 수수료 0.015%, 원 미만 버림 */
export function tradeFee(amount: number): number {
  if (amount <= 0) return 0;
  return Number((BigInt(amount) * 15n) / 100000n);
}

/** 다른 회원에게 주식을 보낼 때 수수료: 현재가 × 수량 × 1%, 원 미만 버림 */
export function stockTransferFee(price: number, quantity: number): number {
  if (price <= 0 || quantity <= 0) return 0;
  return Number((BigInt(price) * BigInt(quantity)) / 100n);
}

/** 매수 때 묶이는 금액 = 주문가 × 수량 + 수수료 */
export function buyHoldAmount(price: number, quantity: number): number {
  const amount = price * quantity;
  return amount + tradeFee(amount);
}

/** 주문 가능 금액 안에서 살 수 있는 가장 큰 수량(서버 상한 1,000,000주) */
export function maxBuyQuantity(price: number, orderable: number): number {
  if (price <= 0 || orderable <= 0) return 0;
  let q = Math.min(1_000_000, Math.floor(orderable / price));
  while (q > 0 && buyHoldAmount(price, q) > orderable) q--;
  return q;
}
