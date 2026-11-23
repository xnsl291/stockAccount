package zb.accountMangement.common.type;

import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@AllArgsConstructor
public enum FeeRate {
    TRANSFER_FEE_RATE(new BigDecimal("0.01")),
    TRADE_FEE_RATE(new BigDecimal("0.00015")),

    ;
    private final BigDecimal rate;

    /**
     * 금액에 대한 수수료 - 원 미만은 버림
     */
    public long of(long amount) {
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
