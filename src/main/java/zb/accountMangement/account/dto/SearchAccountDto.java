package zb.accountMangement.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import zb.accountMangement.account.model.AccountStatus;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class SearchAccountDto {

    @NotNull
    private Long accountId;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String ownerName;

    private String accountNickname;

    @NotBlank
    private Double balance = 0.0;

    @NotBlank
    private AccountStatus status;
}
