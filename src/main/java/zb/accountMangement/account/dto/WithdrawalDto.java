package zb.accountMangement.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalDto {

    @NotNull
    private Long accountId;

    @NotNull
    private Double amount;

    private String memo;

    @NotBlank
    private String accountPassword;
}
