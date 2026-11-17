package zb.accountMangement.account.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class TransferDto {

    @NotNull
    private Double amount;

    private String memo;

    @NotBlank
    private String accountPassword; // 보내는 계좌의 비밀번호
}
