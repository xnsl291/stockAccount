package zb.accountMangement.account.model;

import lombok.RequiredArgsConstructor;

/**
 * 거래 종류. 거래 기록의 금액은 잔액이 늘면 양수, 줄면 음수로 남기므로
 * 한 계좌의 거래 금액을 모두 더하면 그 계좌의 잔액이 됨
 */
@RequiredArgsConstructor
public enum TransactionType {
  DEPOSIT(1),
  WITHDRAW(-1),
  TRANSFER_OUT(-1),
  TRANSFER_IN(1),
  STOCK_BUY(-1),
  STOCK_SELL(1),
  FEE(-1);

  private final int sign;

  public long signed(long amount) {
    return sign * amount;
  }
}
