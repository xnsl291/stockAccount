package zb.accountMangement.account.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

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
