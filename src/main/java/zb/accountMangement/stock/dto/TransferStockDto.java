package zb.accountMangement.stock.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Getter
@Setter
public class TransferStockDto {

    @NotNull
    private Long senderAccountId;

    @NotNull
    private Long receiverAccountId;

    @NotNull
    private Long stockId;

    @Positive
    private int quantity;

    @NotBlank
    private String accountPassword; // 보내는 계좌의 비밀번호
}
