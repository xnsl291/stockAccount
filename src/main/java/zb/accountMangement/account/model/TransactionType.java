package zb.accountMangement.account.model;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * 거래 종류. 거래 기록의 금액은 잔액이 늘면 양수, 줄면 음수로 남기므로
 * 한 계좌의 거래 금액을 모두 더하면 그 계좌의 잔액이 됨
 */
@RequiredArgsConstructor
public enum TransactionType {
  DEPOSIT(1, SystemAccount.EXTERNAL_CASH),
  WITHDRAW(-1, SystemAccount.EXTERNAL_CASH),
  // 송금은 두 회원의 금액이 이미 서로 반대라 시스템 계정이 끼지 않음
  TRANSFER_OUT(-1, null),
  TRANSFER_IN(1, null),
  STOCK_BUY(-1, SystemAccount.TRADE_COUNTERPARTY),
  STOCK_SELL(1, SystemAccount.TRADE_COUNTERPARTY),
  FEE(-1, SystemAccount.FEE_INCOME);

  private final int sign;
  private final SystemAccount systemAccount;

  public long signed(long amount) {
    return sign * amount;
  }

  public Optional<SystemAccount> systemAccount() {
    return Optional.ofNullable(systemAccount);
  }
}
