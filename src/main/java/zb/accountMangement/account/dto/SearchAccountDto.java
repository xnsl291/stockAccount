package zb.accountMangement.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import zb.accountMangement.account.model.AccountStatus;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class SearchAccountDto {

    private Long accountId;

    private String accountNumber;

    private String ownerName;

    private String accountNickname;

    private Long balance;

    private AccountStatus status;
}
