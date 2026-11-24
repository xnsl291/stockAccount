package zb.accountMangement.account.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Getter
@Setter
public class TransferDto {

    @NotNull
    @Positive
    private Long amount;

    private String memo;

    @NotBlank
    private String accountPassword; // 보내는 계좌의 비밀번호
}
