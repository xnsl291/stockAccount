package zb.accountMangement.common.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public enum FeeRate {
    TRANSFER_FEE_RATE(new BigDecimal("0.01")),

    ;
    private final BigDecimal rate;
}
