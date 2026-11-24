package zb.accountMangement.account.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Getter
@Setter
public class WithdrawalDto {

    @NotNull
    private Long accountId;

    @NotNull
    @Positive
    private Long amount;

    private String memo;

    @NotBlank
    private String accountPassword;
}
