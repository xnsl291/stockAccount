package zb.accountMangement.account.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 거래 기록이 생긴 원인. 네 가지 중 하나만 될 수 있고, 거래 기록의 원인 칸 네 개로 옮겨짐
 */
public sealed interface TransactionCause {
  TransactionCause NONE = new None();

  default Long orderId() {
    return null;
  }

  default Long stockTransferId() {
    return null;
  }

  default UUID cashTransferId() {
    return null;
  }

  default Long counterpartyAccountId() {
    return null;
  }

  record None() implements TransactionCause {
  }

  record Order(Long orderId) implements TransactionCause {
    public Order {
      Objects.requireNonNull(orderId);
    }
  }

  record StockTransfer(Long stockTransferId) implements TransactionCause {
    public StockTransfer {
      Objects.requireNonNull(stockTransferId);
    }
  }

  record CashTransfer(UUID cashTransferId, Long counterpartyAccountId) implements TransactionCause {
    public CashTransfer {
      Objects.requireNonNull(cashTransferId);
      Objects.requireNonNull(counterpartyAccountId);
    }
  }
}
