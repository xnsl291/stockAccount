package zb.accountMangement.stock.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Getter
@Setter
public class TradeStockDto {

    @NotNull
    private Long accountId;

    @NotNull
    private Long stockId;

    @NotNull
    @Positive
    private Long price;

    @Positive
    @Max(1_000_000)
    private int quantity;

    @NotBlank
    private String accountPassword;
}
