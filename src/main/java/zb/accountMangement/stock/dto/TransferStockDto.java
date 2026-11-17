package zb.accountMangement.stock.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDateTime;

@Getter
@Setter
public class TransferStockDto {

    @NotNull
    private Long senderAccountId;

    @NotNull
    private Long receiverAccountId;

    @NotNull
    private Long stockId;

    @NotNull
    @Positive
    private Double price;

    @Positive
    private int quantity;

    @NotBlank
    private String accountPassword; // 보내는 계좌의 비밀번호

    private LocalDateTime transferAt;
}
