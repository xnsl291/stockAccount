package zb.accountMangement.account.model;

/**
 * 회원 돈이 움직일 때 반대편이 되는 시스템 계정. 회원 거래와 시스템 기록의 금액을 모두 더하면 0이 됨
 */
public enum SystemAccount {
  EXTERNAL_CASH, // 시스템 밖(은행)과 주고받은 돈
  TRADE_COUNTERPARTY, // 주식을 사고판 상대
  FEE_INCOME // 회원이 낸 수수료
}
