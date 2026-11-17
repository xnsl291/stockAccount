package zb.accountMangement.account.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class DepositDto {

    @NotNull
    private Long accountId;

    @NotNull
    private Double amount;

    private String memo;
}
