package zb.accountMangement.account.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Getter
@Setter
public class DepositDto {

    @NotNull
    private Long accountId;

    @NotNull
    @Positive
    private Long amount;

    private String memo;
}
